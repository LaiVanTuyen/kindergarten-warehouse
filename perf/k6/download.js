// ===========================================================
// Baseline k6 — TẢI FILE (đo riêng, không trộn với bài đọc)
// ===========================================================
// Đo riêng vì hiện tại download còn stream qua Spring
// (StreamingResponseBody): mỗi lượt tải giữ một thread servlet suốt
// thời gian truyền. Đây chính là con số cần so ở Tuần 7 sau khi
// chuyển sang X-Accel-Redirect.
//
// Chạy có đăng nhập, dù code hiện tại vẫn cho guest tải — để số liệu
// so sánh được với Tuần 7, lúc đó GUEST sẽ bị chặn bằng mã 6011.
//
// download-ids.json chứa 99 resource trỏ vào MỘT object thật ~880KB
// trong MinIO. Dùng chung object là cố ý: loại bỏ khác biệt do kích
// thước file, chỉ còn đo chi phí của đường truyền.
// ===========================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://portal';
const DUR = __ENV.DURATION || '60s';
const SCALE = Number(__ENV.VU_SCALE || 1);
const EMAIL = __ENV.LOGIN_EMAIL;
const PASSWORD = __ENV.LOGIN_PASSWORD;

const IDS = JSON.parse(open('./download-ids.json'));

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
  return { jar };
}

export default function (data) {
  const cookie = Object.entries(data.jar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
  const id = IDS[Math.floor(Math.random() * IDS.length)];

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
