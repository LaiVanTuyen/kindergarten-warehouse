# FE Integration Guide — Account Management Overhaul

> Tài liệu dành cho team FE. Mô tả **mọi thay đổi** của backend liên quan đến tài khoản / user / auth.
> Base URL: `https://<domain>/api/v1`. Mọi request/response dùng JSON trừ upload multipart.

---

## 1. Tóm tắt thay đổi lớn

| # | Thay đổi | Tác động FE |
|---|---------|-------------|
| 1 | Register **không còn auto-login** | Sau `/auth/register` phải điều hướng user sang trang "Xác thực email", không set session |
| 2 | User mới có status `PENDING` | Không login được cho tới khi verify email |
| 3 | Thêm flow **quên mật khẩu** cho user thường | Thêm page "Quên mật khẩu" và "Đặt lại mật khẩu" ngoài trang login |
| 4 | Thêm flow **xác thực email** | Trang nhập OTP sau register + nút "Gửi lại OTP" |
| 5 | Endpoint `GET /users/me` — không còn decode JWT ở FE | Thay chỗ nào đang decode JWT bằng gọi `/me` sau login |
| 6 | JWT bị **revoke tự động** khi admin block / user đổi pass / admin thay role | FE phải handle 401 → redirect login |
| 7 | Rate-limit login sau 5 lần sai / 15 phút theo (email + IP) | Hiển thị thông báo "Thử lại sau X phút" |
| 8 | Block user kèm **lý do** (tùy chọn) | Admin UI thêm input `reason` khi block |
| 9 | Thêm **status mới**: `PENDING`, `INACTIVE` (ngoài `ACTIVE`, `BLOCKED`) | UI filter + badge hiển thị |
| 10 | Admin **không thể** xóa/khóa/hạ quyền chính mình hoặc admin cuối cùng | FE disable nút tương ứng trên UI + handle error |
| 11 | Avatar giới hạn 5MB và chỉ chấp nhận JPEG/PNG/WebP | Validate ở FE trước khi upload |

---

## 2. Response format (không đổi)

```json
{
  "code": 1000,
  "message": "Login successful",
  "result": { ... },
  "timestamp": "2026-04-21T15:30:00.123"
}
```

- `code = 1000` → success. Khác đi → error (xem bảng error code mục 6).
- `result` có thể `null` cho các endpoint không trả dữ liệu.

---

## 3. Auth endpoints

### 3.1. `POST /auth/register`

**Flow cũ**: register → auto-login → có token.
**Flow mới**: register → status `PENDING` → gửi OTP qua email → FE điều hướng sang trang verify.

**Request**
```json
{
  "username": "teacher_an",
  "email": "an@example.com",
  "password": "Str0ngP@ss",
  "fullName": "Cô Giáo An"
}
```

**Response 201**
```json
{
  "code": 1000,
  "message": "Registered successfully. Please check your email to verify the account.",
  "result": {
    "user": { /* UserResponse */ },
    "accessToken": null,
    "refreshToken": null
  }
}
```

**Hành động FE**: lưu email user vừa nhập, redirect sang `/verify-email?email=<email>`.

---

### 3.2. `POST /auth/verify-email`

**Request**
```json
{
  "email": "an@example.com",
  "otp": "123456"
}
```

**Response 200**
```json
{ "code": 1000, "message": "Email verified successfully", "result": null }
```

**Hành động FE**: thông báo thành công, điều hướng sang trang login.

**Error**:
- `1014 INVALID_OTP` — OTP sai hoặc hết hạn.
- `1015 OTP_ATTEMPT_EXCEEDED` — sai quá 5 lần trong 15 phút, phải request lại.

---

### 3.3. `POST /auth/resend-verification`

**Request**
```json
{ "email": "an@example.com" }
```

**Response 200** (luôn 200 kể cả email không tồn tại — không leak danh sách email)
```json
{ "code": 1000, "message": "Verification email has been resent", "result": null }
```

---

### 3.4. `POST /auth/login`

**Request**
```json
{
  "email": "an@example.com",   // hoặc username
  "password": "Str0ngP@ss"
}
```

**Response 200** — cookie `accessToken` HttpOnly được set tự động.
```json
{
  "code": 1000,
  "message": "Login successful",
  "result": {
    "user": { /* UserResponse */ },
    "accessToken": null,
    "refreshToken": null
  }
}
```

> `accessToken` trong body là `null` có chủ đích — FE dùng cookie HttpOnly, không cần lưu token. Khi gọi API sau đó browser tự đính kèm cookie.

**Error cần FE xử lý rõ ràng**:

| Code | Ý nghĩa | UX gợi ý |
|------|---------|----------|
| `1011 UNAUTHENTICATED` | Sai email/password | "Tài khoản hoặc mật khẩu không đúng" |
| `1016 LOGIN_ATTEMPT_EXCEEDED` | >5 lần sai trong 15' | Đọc header `Retry-After` (giây), hiển thị countdown |
| `1017 EMAIL_NOT_VERIFIED` | Chưa verify email | Nút "Gửi lại OTP xác thực email" |
| `1018 ACCOUNT_BLOCKED` | Bị admin khóa | "Tài khoản bị khóa, liên hệ quản trị viên" |
| `1019 ACCOUNT_DELETED` | Bị xóa mềm | "Tài khoản đã bị vô hiệu hóa" |
| `1020 ACCOUNT_PENDING` | Status `PENDING` nhưng email đã verify (hiếm) | "Tài khoản đang chờ duyệt" |

**Retry-After header (RFC 7231)**: khi 1016/1015 trả về, BE set header `Retry-After: <seconds>`. FE parse:

```ts
const retryAfter = Number(err.headers.get('Retry-After')) || 900; // fallback 15m
startCountdown(retryAfter);
```

---

### 3.5. `POST /auth/logout`

**Request**: không body.
**Response 200**: cookie được xóa, token cũ vào blacklist.
```json
{ "code": 1000, "message": "Logout successful", "result": null }
```

**Hành động FE**: clear state local (Redux/store), redirect về `/login`.

---

### 3.6. `POST /auth/forgot-password` — **MỚI**

User tự gõ email trên trang "Quên mật khẩu".

**Request**
```json
{ "email": "an@example.com" }
```

**Response 200** (luôn 200)
```json
{ "code": 1000, "message": "If the email exists, an OTP has been sent", "result": null }
```

**Hành động FE**: hiển thị "Đã gửi OTP tới email nếu tồn tại" rồi hiện form nhập OTP + password mới.

---

### 3.7. `POST /auth/reset-password` — **MỚI**

**Request**
```json
{
  "email": "an@example.com",
  "otp": "123456",
  "newPassword": "NewStr0ng!Pass"
}
```

**Response 200**
```json
{ "code": 1000, "message": "Password has been reset successfully", "result": null }
```

**Hành động FE**: thông báo, redirect về login. Toàn bộ session cũ trên các device khác sẽ bị vô hiệu (JWT revoke).

**Validation FE**:
- `newPassword`: tối thiểu 8 ký tự, tối đa 100.
- `otp`: 6 chữ số.

---

## 4. User endpoints

### 4.1. `GET /users/me` — **MỚI** (authenticated)

Thay thế việc decode JWT ở FE. Luôn trả user hiện tại dựa trên JWT cookie.

**Response 200**
```json
{
  "code": 1000,
  "message": "Current user retrieved successfully",
  "result": { /* UserResponse */ }
}
```

**Gợi ý FE**:
- Gọi ngay sau khi app boot (nếu cookie có sẵn) để khôi phục session.
- Gọi sau khi login thành công để set user vào store.
- Nếu 401 → cookie expired hoặc token revoked → redirect login.

---

### 4.2. `PUT /users/profile` (authenticated)

**Request** (partial update — chỉ gửi field muốn đổi)
```json
{
  "fullName": "Cô An mới",
  "phoneNumber": "0912345678",
  "bio": "Giáo viên mầm non với 10 năm kinh nghiệm."
}
```

Không thay đổi so với cũ.

---

### 4.3. `PUT /users/change-password` (authenticated)

Sau khi đổi pass thành công, **JWT hiện tại bị vô hiệu hóa**. FE phải redirect login.

**Request**
```json
{
  "currentPassword": "OldPass",
  "newPassword": "NewStr0ngP@ss"
}
```

**Hành động FE**: sau khi nhận 200, clear store + redirect login.

---

### 4.4. `POST /users/avatar` (authenticated, multipart)

**Body**: `file` (multipart, MIME: `image/jpeg`, `image/png`, `image/webp`)
**Giới hạn**: ≤ 5MB.

**Error**:
- `6003 FILE_TYPE_INVALID`
- `6008 AVATAR_TOO_LARGE`

FE validate trước khi gửi để UX tốt hơn:
```js
if (file.size > 5 * 1024 * 1024) showError("Ảnh quá lớn (>5MB)");
if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) showError("Chỉ chấp nhận JPG/PNG/WebP");
```

---

## 5. Admin endpoints (cần role `ADMIN`)

### 5.1. `GET /users?page=&size=&sortBy=&sortDir=&keyword=&roles=&statuses=`

Không đổi signature. Nhưng `statuses` giờ có thêm 2 giá trị: `PENDING`, `INACTIVE`.

**Query**:
```
?statuses=ACTIVE,PENDING,BLOCKED,INACTIVE,DELETED
&roles=ADMIN,TEACHER,USER
&keyword=an
```

**UX gợi ý**:
- Badge màu: `ACTIVE` xanh, `PENDING` vàng, `BLOCKED` đỏ, `INACTIVE` xám, `DELETED` đen.

---

### 5.2. `POST /users`

Không đổi — admin tạo user sẽ có `status = ACTIVE`, `emailVerified = true` mặc định (bỏ qua verify email).

---

### 5.3. `PUT /users/{id}` (admin update)

**Request** (mọi field optional)
```json
{
  "fullName": "...",
  "phoneNumber": "...",
  "bio": "...",
  "status": "ACTIVE",       // hoặc PENDING, BLOCKED, INACTIVE
  "roles": ["TEACHER"]      // Set<String>
}
```

**Error mới**:
- `2006 LAST_ADMIN_PROTECTED` — cố hạ quyền admin cuối cùng.
- `2007 INVALID_ROLE` — role string không tồn tại.

**UX gợi ý**: khi hiển thị list admin, disable nút "Remove ADMIN role" nếu chỉ còn 1 admin active.

---

### 5.4. `DELETE /users/{id}`

Soft-delete. Không thay đổi signature.

**Error mới**:
- `2005 CANNOT_MODIFY_SELF` — admin cố xóa chính mình.
- `2006 LAST_ADMIN_PROTECTED` — admin cuối cùng.

**UX gợi ý**: ẩn nút xóa khi row là `currentUser.id`.

---

### 5.5. `PUT /users/{id}/restore`

Restore user đã xóa mềm. Không đổi signature.

---

### 5.6. `PUT /users/{id}/block` — **ĐÃ ĐỔI**

**Request body mới** (trước đây body rỗng, giờ nhận `reason` optional):
```json
{ "reason": "Đăng tải nội dung không phù hợp" }
```

Body vẫn có thể bỏ trống cho tương thích cũ:
```json
{}
```

**Response**:
```json
{
  "code": 1000,
  "message": "User blocked successfully", // hoặc "User unblocked successfully"
  "result": { /* UserResponse — có thêm blockedReason, blockedAt */ }
}
```

**Behavior**:
- Click 1 lần: `ACTIVE → BLOCKED` (kèm reason, gửi email notify user).
- Click lần 2: `BLOCKED → ACTIVE` (reason=null, gửi email unblocked).

**Error mới**:
- `2005 CANNOT_MODIFY_SELF`
- `2006 LAST_ADMIN_PROTECTED` (khi block admin cuối cùng)

---

### 5.7. `POST /users/{id}/reset-password/init` + `confirm`

Flow cũ **vẫn giữ** để admin reset password cho 1 user cụ thể (ví dụ user mất email). Khuyến nghị user tự dùng `/auth/forgot-password` thay vì endpoint này.

Không đổi signature.

---

## 6. Error code mapping

| Code | Enum | HTTP | Message key i18n |
|------|------|------|-----------------|
| 1011 | UNAUTHENTICATED | 401 | auth.login_failed |
| 1012 | FORBIDDEN | 403 | error.forbidden |
| 1013 | INVALID_PASSWORD | 400 | auth.password.invalid |
| 1014 | INVALID_OTP | 400 | auth.otp.invalid |
| 1015 | OTP_ATTEMPT_EXCEEDED | 429 | auth.otp.attempt_exceeded |
| 1016 | LOGIN_ATTEMPT_EXCEEDED | 429 | auth.login.attempt_exceeded |
| 1017 | EMAIL_NOT_VERIFIED | 403 | auth.email.not_verified |
| 1018 | ACCOUNT_BLOCKED | 403 | auth.account.blocked |
| 1019 | ACCOUNT_DELETED | 403 | auth.account.deleted |
| 1020 | ACCOUNT_PENDING | 403 | auth.account.pending |
| 2001 | USER_EXISTED | 409 | auth.username.taken |
| 2003 | USER_NOT_FOUND | 404 | error.user.not_found |
| 2004 | EMAIL_EXISTED | 409 | auth.email.taken |
| 2005 | CANNOT_MODIFY_SELF | 400 | error.user.cannot_modify_self |
| 2006 | LAST_ADMIN_PROTECTED | 400 | error.user.last_admin |
| 2007 | INVALID_ROLE | 400 | error.user.invalid_role |
| 6003 | FILE_TYPE_INVALID | 400 | error.file.type.invalid |
| 6008 | AVATAR_TOO_LARGE | 400 | error.user.avatar.too_large |

**i18n**: backend hỗ trợ `vi` (mặc định) và `en`. FE gửi header `Accept-Language: vi` hoặc `en` để nhận `message` tương ứng.

---

## 7. UserResponse — schema đầy đủ

```typescript
interface UserResponse {
  id: number;
  username: string;
  fullName: string | null;        // Fallback ở FE: fullName || username
  email: string;
  avatarUrl: string | null;
  roles: string[];                // ["ADMIN", "TEACHER", "USER"]
  status: "PENDING" | "ACTIVE" | "BLOCKED" | "INACTIVE";
  isDeleted: boolean;
  emailVerified: boolean;
  blockedReason: string | null;   // Chỉ khác null khi status=BLOCKED
  blockedAt: string | null;       // ISO 8601
  createdAt: string;              // ISO 8601
  phoneNumber: string | null;
  bio: string | null;
  lastActive: string | null;
}
```

> Field `tokenVersion` KHÔNG expose ra FE (nội bộ).

---

## 8. Authentication state machine (FE nên follow)

```
┌──────────────┐ register ┌──────────────┐ verify OTP ┌──────────────┐
│ (khách vãng) │─────────>│ PENDING      │───────────>│ ACTIVE       │
│              │          │ chờ verify   │            │ có thể login │
└──────────────┘          └──────────────┘            └──────┬───────┘
                                                              │
                                                              │ admin block
                                                              ▼
                                                      ┌──────────────┐
                                                      │ BLOCKED      │
                                                      │ không login  │
                                                      └──────┬───────┘
                                                             │ admin unblock
                                                             ▼
                                                      ACTIVE (quay lại)

ACTIVE ──admin delete──> DELETED (is_deleted=true) ──admin restore──> ACTIVE
```

---

## 9. JWT revocation — FE cần xử lý

Backend tăng `token_version` khi:
- User đổi mật khẩu (qua `/change-password` hoặc `/auth/reset-password`)
- Admin block user
- Admin thay đổi role của user
- User bị admin xóa mềm
- Admin reset password (flow cũ)

Kết quả: **JWT hiện tại bị từ chối** ở request kế tiếp với HTTP 401.

**Global 401 interceptor ở FE**:
```typescript
// axios hoặc fetch wrapper
onResponseError(err) {
  if (err.status === 401 && !isAuthEndpoint(err.config.url)) {
    store.clearAuth();
    router.push("/login?expired=1");
  }
  return Promise.reject(err);
}
```

Hiển thị toast "Phiên đăng nhập đã hết hạn hoặc bị vô hiệu hóa" khi query `expired=1`.

---

## 10. Cookie & CORS notes

- Backend set cookie `accessToken` với `HttpOnly`, `Secure` (prod), `SameSite=None` (prod) hoặc `Lax` (dev).
- FE **phải** gửi request với `credentials: 'include'` / `withCredentials: true`:
  ```js
  // axios
  axios.defaults.withCredentials = true;
  // fetch
  fetch(url, { credentials: 'include' });
  ```
- CORS đã whitelist các origin trong env `CORS_ALLOWED_ORIGINS`. Nếu FE deploy domain mới, báo BE thêm vào env.

---

## 11. Test checklist cho FE

- [ ] Register → redirect verify email, không login tự động
- [ ] Verify email OTP sai 5 lần → báo lỗi 1015 → nút "Gửi lại OTP"
- [ ] Verify email thành công → về login page
- [ ] Login khi email chưa verify → hiện nút "Gửi lại OTP"
- [ ] Login sai 5 lần → hiện lỗi "thử lại sau 15 phút"
- [ ] Login đã BLOCKED → hiện lý do + liên hệ admin
- [ ] Login thành công → gọi `/me` → set user vào store
- [ ] Quên mật khẩu: email → OTP → password mới → về login
- [ ] Đổi mật khẩu (khi đang login) → auto-logout → redirect login
- [ ] Admin block user: có input `reason`, user bị kick khỏi session đang mở ở browser khác
- [ ] Admin tự xóa/khóa/hạ quyền chính mình → UI disable nút
- [ ] Admin hạ quyền admin cuối cùng → error 2006 → toast rõ ràng
- [ ] Upload avatar > 5MB → validate FE trước khi upload
- [ ] Upload avatar sai định dạng → toast "chỉ JPG/PNG/WebP"
- [ ] Global 401 interceptor → redirect login khi token revoked
- [ ] Page `lastActive`, `blockedAt`, `createdAt` parse ISO 8601 đúng timezone

---

## 12. Breaking changes — cần migrate ngay

| Điểm cũ | Điểm mới |
|---------|----------|
| Register → có token trong response | Register → token = null. Redirect verify email. |
| `toggleBlockUser` body rỗng | Body có thể gửi `{ "reason": "..." }` |
| Decode JWT ở FE để lấy user info | Gọi `GET /users/me` |
| Logout chỉ clear local state | Gọi `POST /auth/logout` để blacklist token server |

---

## 13. Non-breaking changes (nice-to-have)

- User giờ có field `lastActive` (đã có từ trước).
- Filter users theo `statuses=DELETED` vẫn work như cũ.
- `/api/v1/users/{id}/reset-password/*` admin flow vẫn giữ nguyên, nhưng khuyến nghị đổi sang `/auth/forgot-password` do user tự khởi tạo.

---

## 14. Câu hỏi thường gặp

**Q: Tại sao register không auto-login?**
A: Để đảm bảo email thật sự thuộc về user, tránh spam tài khoản và người khác đăng ký bằng email của mình. Cũng là chuẩn industry (GitHub, GitLab, Stripe, v.v.).

**Q: Nếu user chưa verify email trong vòng bao lâu thì bị xóa?**
A: Hiện chưa có auto-expire. Account `PENDING` sẽ tồn tại vô hạn cho tới khi verify hoặc bị admin xóa thủ công. Nếu muốn auto-cleanup sau X ngày, báo BE bổ sung.

**Q: OTP có hết hạn không?**
A: Có, 5 phút. Sau 5 lần nhập sai OTP bị invalidate — phải request lại (`/auth/resend-verification` hoặc `/auth/forgot-password`).

**Q: Sau khi đổi pass có cần clear cookie ở FE không?**
A: Backend đã revoke JWT → request kế tiếp sẽ 401. FE có thể hoặc không clear cookie — interceptor 401 sẽ xử lý. Tuy nhiên UX tốt hơn là clear ngay và redirect login.

**Q: Rate limit login có reset khi login thành công không?**
A: Có. Counter `(email+IP)` reset về 0 mỗi khi login thành công.

**Q: User bị BLOCKED có thể tự unblock bằng quên mật khẩu không?**
A: **Không**. `/auth/forgot-password` vẫn gửi OTP cho user BLOCKED, họ reset được password **nhưng** khi login vẫn bị chặn bởi status `BLOCKED`. User buộc phải liên hệ admin để unblock.

**Q: Tôi cần hiển thị `blockedReason` trên UI — BE có trả không?**
A: Hiện `UserResponse` chưa expose `blockedReason`. Nếu cần, báo BE bổ sung. Email gửi cho user khi block đã chứa reason.

---

## 15. Curl examples — test nhanh toàn bộ flow

Đổi `API=` sang domain thật khi test prod.

```bash
API=http://localhost:8080/api/v1
CT='Content-Type: application/json'
COOKIE_JAR=/tmp/cookies.txt
```

### Register → verify → login

```bash
# 1. Register
curl -sS -X POST "$API/auth/register" -H "$CT" -d '{
  "username": "an01",
  "email": "an01@example.com",
  "password": "Str0ngP@ss",
  "fullName": "Cô An"
}' | jq

# 2. Kiểm tra email → lấy OTP (dev: log console, prod: Gmail inbox)

# 3. Verify email
curl -sS -X POST "$API/auth/verify-email" -H "$CT" -d '{
  "email": "an01@example.com",
  "otp": "123456"
}' | jq

# 4. Login (lưu cookie vào $COOKIE_JAR)
curl -sS -c "$COOKIE_JAR" -X POST "$API/auth/login" -H "$CT" -d '{
  "email": "an01@example.com",
  "password": "Str0ngP@ss"
}' | jq

# 5. Get me (dùng cookie)
curl -sS -b "$COOKIE_JAR" "$API/users/me" | jq
```

### Forgot password

```bash
curl -sS -X POST "$API/auth/forgot-password" -H "$CT" -d '{"email":"an01@example.com"}' | jq

# Lấy OTP từ email rồi:
curl -sS -X POST "$API/auth/reset-password" -H "$CT" -d '{
  "email": "an01@example.com",
  "otp": "654321",
  "newPassword": "NewStr0ngP@ss"
}' | jq
```

### Change password (authenticated)

```bash
curl -sS -b "$COOKIE_JAR" -X PUT "$API/users/change-password" -H "$CT" -d '{
  "currentPassword": "Str0ngP@ss",
  "newPassword": "EvenStr0ngerP@ss"
}' | jq
# Sau lệnh này cookie cũ bị vô hiệu, cần login lại.
```

### Update profile

```bash
curl -sS -b "$COOKIE_JAR" -X PUT "$API/users/profile" -H "$CT" -d '{
  "fullName": "Cô An (updated)",
  "phoneNumber": "0912345678",
  "bio": "10 năm kinh nghiệm dạy mầm non"
}' | jq
```

### Upload avatar

```bash
curl -sS -b "$COOKIE_JAR" -X POST "$API/users/avatar" \
  -F "file=@/path/to/avatar.png;type=image/png" | jq
```

### Logout

```bash
curl -sS -b "$COOKIE_JAR" -X POST "$API/auth/logout" | jq
rm "$COOKIE_JAR"
```

### Admin — list users + block

```bash
# Login admin trước để có cookie admin
ADMIN_COOKIE=/tmp/admin.txt
curl -sS -c "$ADMIN_COOKIE" -X POST "$API/auth/login" -H "$CT" -d '{
  "email": "admin@your-domain.com", "password": "<admin-password>"
}' | jq

# List users
curl -sS -b "$ADMIN_COOKIE" "$API/users?page=0&size=10&statuses=ACTIVE,PENDING" | jq

# Block user với lý do
curl -sS -b "$ADMIN_COOKIE" -X PUT "$API/users/42/block" -H "$CT" -d '{
  "reason": "Đăng nội dung vi phạm"
}' | jq

# Unblock (gọi lại cùng endpoint)
curl -sS -b "$ADMIN_COOKIE" -X PUT "$API/users/42/block" -H "$CT" -d '{}' | jq
```

---

**Liên hệ BE**: @tuyen.laivan — mọi câu hỏi về API hoặc yêu cầu bổ sung field hãy tag trong issue GitHub.

---

## Lịch sử thay đổi

| Ngày | Thay đổi |
|------|----------|
| 2026-04-21 | Khởi tạo: register flow + email verify + forgot password + JWT revocation + last-admin guard |
| 2026-04-21 (v2) | UserResponse expose thêm `emailVerified`, `blockedReason`, `blockedAt`. Bổ sung header `Retry-After` cho 1015/1016. |

