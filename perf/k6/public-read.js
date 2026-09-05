// ===========================================================
// Baseline k6 — ĐỌC CÔNG KHAI (guest, không đăng nhập)
// ===========================================================
// Ba kịch bản chạy song song, tách tag để đọc số riêng từng loại:
//   list   200 VU — duyệt danh sách có phân trang
//   search 100 VU — tìm kiếm theo từ khoá + lọc
//   detail  50 VU — mở trang chi tiết theo slug
//
// KHÔNG trộn upload/email vào bài đọc này.
// ===========================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://portal';
const DUR = __ENV.DURATION || '60s';
const SCALE = Number(__ENV.VU_SCALE || 1);

const listTrend = new Trend('t_list', true);
const searchTrend = new Trend('t_search', true);
const detailTrend = new Trend('t_detail', true);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    list: {
      executor: 'constant-vus',
      vus: Math.round(200 * SCALE),
      duration: DUR,
      exec: 'browseList',
      tags: { scenario: 'list' },
    },
    search: {
      executor: 'constant-vus',
      vus: Math.round(100 * SCALE),
      duration: DUR,
      exec: 'search',
      tags: { scenario: 'search' },
    },
    detail: {
      executor: 'constant-vus',
      vus: Math.round(50 * SCALE),
      duration: DUR,
      exec: 'detail',
      tags: { scenario: 'detail' },
    },
  },
  thresholds: {
    // Tiêu chí ban đầu theo kế hoạch: đọc P95 < 500ms, lỗi < 1%
    't_list':   ['p(95)<500'],
    't_search': ['p(95)<500'],
    't_detail': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
  },
};

const KEYWORDS = ['mẫu', 'học liệu', 'hiệu năng', '0042', 'mô tả'];

export function browseList() {
  const page = Math.floor(Math.random() * 20);
  const res = http.get(`${BASE}/api/v1/resources?page=${page}&size=12`, {
    tags: { name: 'GET /resources' },
  });
  listTrend.add(res.timings.duration);
  check(res, { 'list 200': (r) => r.status === 200 });
}

export function search() {
  const kw = encodeURIComponent(KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)]);
  const age = 1 + Math.floor(Math.random() * 3);
  const res = http.get(
    `${BASE}/api/v1/resources?keyword=${kw}&ageGroupId=${age}&page=0&size=12`,
    { tags: { name: 'GET /resources?keyword' } }
  );
  searchTrend.add(res.timings.duration);
  check(res, { 'search 200': (r) => r.status === 200 });
}

export function detail() {
  const n = 1 + Math.floor(Math.random() * 1700); // chỉ lấy phần PUBLIC+APPROVED
  const slug = `perf-seed-${String(n).padStart(4, '0')}`;
  const res = http.get(`${BASE}/api/v1/resources/${slug}`, {
    tags: { name: 'GET /resources/{slug}' },
  });
  detailTrend.add(res.timings.duration);
  check(res, { 'detail 200': (r) => r.status === 200 });
}
