// ===========================================================
// Baseline k6 — ĐỌC CÓ ĐĂNG NHẬP
// ===========================================================
// Đo thêm chi phí của filter JWT + kiểm tra quyền so với đọc công khai.
// So sánh trực tiếp t_auth_list ở đây với t_list của public-read.js.
//
// Mỗi VU đăng nhập một lần trong setup rồi tái dùng cookie —
// không đo chi phí đăng nhập trong vòng lặp.
// ===========================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://portal';
const DUR = __ENV.DURATION || '60s';
const SCALE = Number(__ENV.VU_SCALE || 1);
const EMAIL = __ENV.LOGIN_EMAIL;
const PASSWORD = __ENV.LOGIN_PASSWORD;

const authListTrend = new Trend('t_auth_list', true);
const myResTrend = new Trend('t_my_resources', true);

export const options = {
  scenarios: {
    authList: {
      executor: 'constant-vus',
      vus: Math.round(100 * SCALE),
      duration: DUR,
      exec: 'authList',
      tags: { scenario: 'auth_list' },
    },
    myResources: {
      executor: 'constant-vus',
      vus: Math.round(50 * SCALE),
      duration: DUR,
      exec: 'myResources',
      tags: { scenario: 'my_resources' },
    },
  },
  thresholds: {
    't_auth_list': ['p(95)<500'],
    't_my_resources': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
  },
};

export function setup() {
  if (!EMAIL || !PASSWORD) {
    throw new Error('Thiếu LOGIN_EMAIL / LOGIN_PASSWORD');
  }
  // /api/v1/auth/login được miễn CSRF (SecurityConfig) nên POST thẳng được
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`Đăng nhập thất bại: HTTP ${res.status} — ${res.body}`);
  }
  const cookies = res.cookies;
  const jar = {};
  for (const name of Object.keys(cookies)) {
    jar[name] = cookies[name][0].value;
  }
  return { jar };
}

function cookieHeader(jar) {
  return Object.entries(jar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

export function authList(data) {
  const page = Math.floor(Math.random() * 20);
  const res = http.get(`${BASE}/api/v1/resources?page=${page}&size=12`, {
    headers: { Cookie: cookieHeader(data.jar) },
    tags: { name: 'GET /resources (auth)' },
  });
  authListTrend.add(res.timings.duration);
  check(res, { 'auth list 200': (r) => r.status === 200 });
}

export function myResources(data) {
  const res = http.get(`${BASE}/api/v1/resources/me?page=0&size=12`, {
    headers: { Cookie: cookieHeader(data.jar) },
    tags: { name: 'GET /resources/me' },
  });
  myResTrend.add(res.timings.duration);
  check(res, { 'me 200': (r) => r.status === 200 });
}
