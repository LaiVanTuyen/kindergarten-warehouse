// ===========================================================
// Baseline k6 — TẢI FILE (đo riêng, không trộn với bài đọc)
// ===========================================================
// Đo riêng vì hiện tại download còn stream qua Spring
// (StreamingResponseBody): mỗi lượt tải giữ một thread servlet suốt
// thời gian truyền. Đây chính là con số cần so ở Tuần 7 sau khi
// chuyển sang X-Accel-Redirect.
//
// Chạy CÓ đăng nhập: guest bị chặn bằng mã 6011 (contract §3), nên bài đo
// không đăng nhập sẽ chỉ đo tốc độ trả 401.
//
// Danh sách id lấy từ API trong setup(), không dùng file tĩnh. Mọi resource
// của bộ seed trỏ vào MỘT object dùng chung ~880KB trong MinIO — cố ý, để loại
// bỏ khác biệt do kích thước file, chỉ còn đo chi phí đường truyền.
// ===========================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://portal';
const DUR = __ENV.DURATION || '60s';
const SCALE = Number(__ENV.VU_SCALE || 1);
const EMAIL = __ENV.LOGIN_EMAIL;
const PASSWORD = __ENV.LOGIN_PASSWORD;

const dlTrend = new Trend('t_download', true);
const dlBytes = new Counter('download_bytes');

export const options = {
  scenarios: {
    download: {
      executor: 'constant-vus',
      vus: Math.round(50 * SCALE),
      duration: DUR,
      tags: { scenario: 'download' },
    },
  },
  thresholds: {
    // Ngưỡng nới hơn bài đọc: đây là truyền file ~880KB, không phải API đọc
    't_download': ['p(95)<3000'],
    'http_req_failed': ['rate<0.01'],
  },
};

export function setup() {
  if (!EMAIL || !PASSWORD) throw new Error('Thiếu LOGIN_EMAIL / LOGIN_PASSWORD');
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`Đăng nhập thất bại: HTTP ${res.status}`);
  }
  const jar = {};
  for (const name of Object.keys(res.cookies)) {
    jar[name] = res.cookies[name][0].value;
  }
  const cookie = Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ');

  // Lấy id TỪ API, không đọc từ file tĩnh.
  //
  // Bản cũ đọc `download-ids.json` — 99 id sinh ra từ một lần seed trước đó.
  // Mỗi lần chạy lại seed, `UUID()` sinh id mới, nên file tĩnh thành rác và bài
  // đo nhận 404 hàng loạt: 100 % check thất bại, trông hệt như hồi quy hiệu
  // năng. Đã xảy ra ngày 2026-09-06.
  const listRes = http.get(`${BASE}/api/v1/resources?page=0&size=100`, {
    headers: { Cookie: cookie },
  });
  if (listRes.status !== 200) {
    throw new Error(`Không lấy được danh sách resource: HTTP ${listRes.status}`);
  }
  const ids = JSON.parse(listRes.body).result.content.map((r) => r.id);
  if (ids.length === 0) {
    throw new Error('Danh sách resource rỗng — đã chạy seed-baseline.sql chưa?');
  }

  return { jar, ids };
}

export default function (data) {
  const cookie = Object.entries(data.jar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];

  const res = http.get(`${BASE}/api/v1/resources/${id}/file`, {
    headers: { Cookie: cookie },
    tags: { name: 'GET /resources/{id}/file' },
  });

  dlTrend.add(res.timings.duration);
  dlBytes.add(res.body ? res.body.length : 0);

  check(res, {
    'download 200': (r) => r.status === 200,
    'nhận đủ file': (r) => r.body && r.body.length > 800000,
  });
}
