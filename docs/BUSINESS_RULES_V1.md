# BUSINESS RULES V1 — Kho học liệu mầm non

Trạng thái: **CHỐT** cho V1. Mọi thay đổi phải cập nhật kèm
[API_CONTRACT_V2.md](API_CONTRACT_V2.md).

Tài liệu này là nguồn sự thật về nghiệp vụ. Khi code và tài liệu mâu thuẫn,
sửa cho khớp — không bên nào được mặc định đúng. V1 của contract đã lệch với
code (FE gọi `/me/resources`, BE phục vụ `/resources/me`) và đó là lý do V2
có thêm cột trạng thái triển khai.

---

## 1. Phạm vi V1

Hệ thống phục vụ **một trường duy nhất**.

Không có trong V1: `school_id`, multi-tenant, subdomain riêng theo trường,
Super Admin đa trường, VIP/thanh toán, quảng cáo, AI tóm tắt, mạng xã hội
người đăng.

Hệ quả: mọi truy vấn không cần lọc theo trường. Nếu sau này mở rộng nhiều
trường, `school_id` sẽ phải thêm vào `users` và `resources` và chạm vào toàn
bộ tầng truy vấn — đó là thay đổi lớn, không phải phần mở rộng.

---

## 2. Vai trò

Enum `Role` hiện có `ADMIN`, `TEACHER`, `USER`. `GUEST` không phải một role
trong DB — đó là người dùng chưa đăng nhập.

| Vai trò | Mô tả |
|---|---|
| GUEST | Chưa đăng nhập. Xem và preview tài nguyên `PUBLIC` đã duyệt. **Không tải được file** (§8.4). |
| USER | Phụ huynh/người dùng đã đăng nhập. Xem, tải, yêu thích, bình luận, đánh giá, báo cáo. |
| TEACHER | Như USER, cộng đăng và quản lý học liệu **của chính mình**. |
| ADMIN | Kiểm duyệt, quản trị toàn trường, xử lý báo cáo, xem audit log. |

Tài khoản admin được seed có đồng thời `ADMIN` và `TEACHER`.

---

## 3. Ma trận quyền visibility phân tầng

### 3.1 Thứ tự mức chặt

```
PUBLIC  <  INTERNAL  <  PRIVATE
(lỏng nhất)          (chặt nhất)
```

### 3.2 Quy tắc hiệu lực

Visibility hiệu lực của một tài nguyên là **mức chặt nhất** trong chuỗi:

```
Category → Topic → Resource
```

Ví dụ bắt buộc phải đúng:

| Category | Topic | Resource | Hiệu lực | Khách thấy? |
|---|---|---|---|---|
| PUBLIC | PUBLIC | PUBLIC | PUBLIC | Có |
| PUBLIC | INTERNAL | PUBLIC | **INTERNAL** | Không |
| PUBLIC | PUBLIC | INTERNAL | INTERNAL | Không |
| INTERNAL | PUBLIC | PUBLIC | **INTERNAL** | Không |
| PUBLIC | PUBLIC | PRIVATE | PRIVATE | Không |
| PRIVATE | PUBLIC | PUBLIC | **PRIVATE** | Không |

### 3.3 Ai xem được gì

| Hiệu lực | GUEST | USER | TEACHER (không sở hữu) | TEACHER (sở hữu) | ADMIN |
|---|---|---|---|---|---|
| PUBLIC | Có | Có | Có | Có | Có |
| INTERNAL | **Không** | Có | Có | Có | Có |
| PRIVATE | Không | Không | **Không** | Có | Có |

### 3.4 Điều kiện hiển thị đầy đủ

Một tài nguyên chỉ xuất hiện trên Portal khi **đồng thời**:

1. `status = APPROVED`
2. `isDeleted = false` — kể cả Topic và Category cha
3. Visibility hiệu lực phù hợp với người xem theo bảng 3.3

Chủ sở hữu và ADMIN vẫn xem được tài nguyên chưa duyệt qua màn "Tài liệu của
tôi" và hàng đợi kiểm duyệt — đó là đường riêng, không phải Portal.

### 3.5 Banner

Banner xét **độc lập**, không thuộc chuỗi Category → Topic. Nhưng vẫn theo
đúng bảng 3.3: banner `INTERNAL` chỉ hiện với người đã đăng nhập.

### 3.6 Cảnh báo triển khai

Hiện tại visibility được so sánh **bằng** với `PUBLIC` ở **22 chỗ trong 10
file** (`Resource`, `Topic`, `Category`, `Banner` và các service tương ứng).
Khi thêm `INTERNAL`, mọi chỗ đó phải đổi sang so sánh theo thứ bậc.

Bỏ sót một chỗ sẽ dẫn tới **lộ nội dung nội bộ cho khách** hoặc **làm biến
mất nội dung công khai**. Vì vậy:

- Phải có **một hàm duy nhất** tính visibility hiệu lực; mọi nơi gọi vào đó.
  Không lặp lại logic so sánh rải rác.
- Phải có integration test cho **từng ô** của bảng 3.3 trước khi merge.

---

## 4. Vòng đời tài nguyên

```
DRAFT
  │ gửi duyệt (teacher)
  ▼
PENDING ──approve──► APPROVED ──archive──► ARCHIVED
  │                     │
  │                     └──sửa nội dung──► PENDING
  └──reject──► REJECTED ──sửa──► PENDING
```

`status` và `visibility` là **hai thuộc tính độc lập**. Một tài nguyên
`APPROVED` + `PRIVATE` là hợp lệ: đã duyệt nhưng chỉ chủ sở hữu và admin xem.

Enum `ResourceStatus` hiện chỉ có `PENDING`, `APPROVED`, `REJECTED`.
V1 bổ sung `DRAFT` và `ARCHIVED`.

### 4.1 Quy tắc

- Teacher lưu nháp không giới hạn. `DRAFT` không hiện ở bất kỳ đâu ngoài màn
  "Tài liệu của tôi" của chính chủ sở hữu.
- Teacher chỉ gửi duyệt được khi đã nhập đủ trường bắt buộc (mục 6.1).
- Admin approve/reject kèm ghi chú. Lý do từ chối lưu ở `rejectionReason` và
  hiển thị cho chủ sở hữu.
- Admin có thể chuyển tài nguyên đã duyệt sang `ARCHIVED`. Tài nguyên
  `ARCHIVED` biến mất khỏi Portal nhưng **không bị xóa** — xem §4.3.
- Mọi `approve`/`reject`/`archive`/`takedown` ghi vào
  `resource_moderation_history` **và** audit log.

### 4.2 Mặc định khi tạo mới

Tài nguyên tạo qua wizard mặc định `DRAFT`. Cột DB hiện đang
`DEFAULT 'PENDING'` (migration V7) nên cần đổi default kèm migration.

### 4.3 Mã trạng thái khi tài nguyên không khả dụng

Ba tình huống khác nhau, **không được gộp thành một mã**:

| Tình huống | HTTP | Mã lỗi | FE hiển thị |
|---|---|---|---|
| Tài nguyên từng `PUBLIC`, nay `ARCHIVED` | **410 Gone** | `RESOURCE_ARCHIVED` (6010) | Trang "Tài liệu không còn được cung cấp" |
| Tài nguyên `INTERNAL`/`PRIVATE` mà người gọi không có quyền | **404** | `RESOURCE_NOT_FOUND` (6001) | Trang 404 thường |
| ID/slug chưa từng tồn tại | **404** | `RESOURCE_NOT_FOUND` (6001) | Trang 404 thường |

Lý do phân biệt: 410 đúng ngữ nghĩa HTTP cho "đã từng có, nay bị gỡ vĩnh viễn",
và thân thiện hơn 404 chung chung với người dùng đang mở link cũ.

**Nhưng tuyệt đối không dùng 410 cho tài nguyên `INTERNAL`/`PRIVATE`.** Hai
trường hợp dưới bắt buộc trả 404 giống hệt nhau — nếu trả 410 hoặc 403 thì
người ngoài sẽ suy ra được tài nguyên đó *có tồn tại*, tức là rò rỉ thông tin
qua chính mã trạng thái.

Hệ quả khi triển khai: quyết định trả 410 hay 404 phải xảy ra **sau** khi đã
tính visibility hiệu lực (§3.2). Chỉ tài nguyên mà người gọi *đáng lẽ được
xem* mới đủ điều kiện nhận 410.

---

## 5. Trường nào khi sửa sẽ đưa tài nguyên về PENDING

Áp dụng cho tài nguyên đang ở `APPROVED`.

### 5.1 KHÔNG cần duyệt lại

| Trường | Ghi chú |
|---|---|
| `visibility` | Đã có endpoint riêng `PATCH /resources/{id}/visibility`. Ghi audit. |

Chỉ duy nhất `visibility`. Lý do: đổi visibility là **thu hẹp hoặc mở rộng
phạm vi của nội dung đã được duyệt**, không tạo ra nội dung mới.

### 5.2 PHẢI về PENDING

| Nhóm | Trường |
|---|---|
| Nội dung | `title`, `description`, `objectives`, `highlights` |
| Tệp | `fileUrl`, `thumbnailUrl`, link YouTube/ngoài |
| Phân loại | `categoryId`, `topicId`, `ageGroups`, `documentType`, `learningDomain`, `teachingMethod`, `audience` |
| Từ khóa | `tags` |
| Nguồn | `authorName`, `sourceName`, `sourceUrl`, `licenseType` |

`tags` và `highlights` **không** được miễn duyệt: chúng là nội dung công khai,
hiển thị trên Portal và dùng để tìm kiếm. Code không có cách nào xác định một
thay đổi văn bản là "nhỏ" hay "an toàn".

Ngoài ra, sửa các trường ở nhóm **Tệp** và **Nguồn** còn làm mất hiệu lực xác
nhận bản quyền — Teacher phải xác nhận lại mới gửi duyệt được (§9.3).

### 5.3 Admin sửa

ADMIN sửa tài nguyên đã duyệt **có thể giữ nguyên `APPROVED`** — vì admin
chính là người duyệt. Nhưng mọi thay đổi phải ghi audit với đầy đủ giá trị
trước/sau.

### 5.4 Hệ quả cho FE

Màn sửa tài liệu phải cảnh báo **trước khi lưu**: *"Thay đổi này sẽ đưa tài
liệu về trạng thái Chờ duyệt và tạm ẩn khỏi Portal."* Không được để teacher
phát hiện sau khi đã lưu.

---

## 6. Phân loại học liệu

```
Category
└── Topic
    └── Resource
        ├── AgeGroup       (nhiều)
        ├── DocumentType   (một)
        ├── LearningDomain (nhiều)
        ├── TeachingMethod (nhiều)
        ├── Audience       (nhiều)
        └── Tag            (nhiều, tự do)
```

Tất cả trừ `Tag` là **dữ liệu có kiểm soát**, cố định bằng migration/seeder.
Admin không phải chạy SQL thủ công, nhưng V1 cũng chưa có màn quản lý
taxonomy — danh mục được seed sẵn.

### 6.1 Trường bắt buộc khi gửi duyệt

`title`, `description`, `categoryId`, `topicId`, ít nhất một `ageGroup`,
`documentType`, ít nhất một `learningDomain`, nguồn tài nguyên (`fileUrl` hoặc
link), và **xác nhận bản quyền còn hiệu lực** (`copyrightDeclaration` +
`copyrightConfirmedAt`, xem §9).

Thiếu bất kỳ trường nào ở trên → 400 `VALIDATION_ERROR`.
Riêng thiếu/mất hiệu lực xác nhận bản quyền → 400 `COPYRIGHT_NOT_CONFIRMED`
(6012), để FE đưa người dùng thẳng về bước xác nhận thay vì bắt dò cả form.

### 6.2 Ba loại "type" — định nghĩa bắt buộc

Đây là chỗ dễ điền nhầm nhất. Ghi rõ để FE và BE không hiểu khác nhau:

| Trường | Ý nghĩa | Giá trị |
|---|---|---|
| `resourceType` | **Nguồn** tài nguyên | `FILE`, `YOUTUBE`, `EXTERNAL_LINK` — enum đã có đủ |
| `fileType` | **Định dạng kỹ thuật** | `PDF`, `VIDEO`, `IMAGE`, `POWERPOINT`, `WORD`… |
| `documentType` | **Ý nghĩa nghiệp vụ** | `LESSON_PLAN`, `WORKSHEET`, `STORY`, `GAME`, `VIDEO_LESSON`, `FORM`, `INITIATIVE` |

Lưu ý: filter `types` trong `ResourceFilterRequest` hiện mang nghĩa `fileType`
dù tên gợi ý `resourceType`. V2 phải đổi tên thành `fileTypes` và thêm
`documentTypes` riêng.

### 6.3 Nhóm tuổi

Hiện seed 3 nhóm: 3–4, 4–5, 5–6 tuổi. V1 bổ sung **Nhà trẻ (dưới 3 tuổi)**.

### 6.4 Lĩnh vực phát triển

Thể chất; Nhận thức; Ngôn ngữ; Tình cảm và kỹ năng xã hội; Thẩm mỹ.

### 6.5 Phương pháp

STEAM; Montessori; Reggio Emilia; Truyền thống.

### 6.6 Đối tượng

Giáo viên; Phụ huynh; Cán bộ quản lý.

### 6.7 Chủ đề

**Chủ đề chính là `Topic`** (động vật, thực vật, gia đình, nghề nghiệp, giao
thông, trường mầm non…). Không tạo chiều phân loại thứ hai trùng nghĩa.

### 6.8 `highlights`

Giữ nguyên vai trò: các gạch đầu dòng giới thiệu hiển thị ở trang chi tiết.
**Không** dùng thay `tags` và không dùng để lọc.

---

## 7. Rating và bình luận

### 7.1 Hiện trạng cần sửa

`rating` đang nằm **trong bảng `comments`** (`rating INT DEFAULT 5`, migration
V6). Một người bình luận N lần thì có N điểm. `averageRating` được tính lại từ
bảng `comments` qua `CommentRepository.recalculateAverageRating`.

### 7.2 Quy tắc V1

- Rating tách sang bảng `ratings` riêng với `UNIQUE(user_id, resource_id)`.
- Mỗi người **một** rating trên một tài nguyên, được phép sửa điểm.
- Bình luận **không bắt buộc** kèm rating.
- Xóa bình luận **không** làm mất rating.
- `averageRating` tính lại từ `ratings`, không từ `comments`.
- Chỉ người đã đăng nhập mới đánh giá; GUEST không đánh giá.

Thiết kế migration và xử lý dữ liệu cũ: xem
[MIGRATION_RATINGS_DESIGN.md](MIGRATION_RATINGS_DESIGN.md).

---

## 8. Thiết kế download

### 8.1 Vì sao không trả presigned URL thẳng cho trình duyệt

Presigned URL đi vòng qua ứng dụng, nên tại thời điểm tải sẽ mất: kiểm tra
role, kiểm tra visibility hiệu lực, `downloadCount`, và khả năng gỡ tài liệu
bị báo cáo — URL đã phát ra vẫn dùng được cho tới khi hết hạn.

### 8.2 Vì sao X-Accel-Redirect thuần cũng không đủ

Policy bucket chỉ mở public-read cho `avatars/`, `banners/`, `icons/`,
`profiles/`, `categories/`, `resources/thumbnails/`. **`resources/files/*` là
private.** Nginx proxy ẩn danh tới đó sẽ nhận **403**.

### 8.3 Thiết kế chốt

**Demo/Production** — có nginx đứng trước ứng dụng:

```
Browser  GET /api/v1/resources/{id}/file
   │
   ▼
Spring   1. kiểm tra đăng nhập + role
         2. kiểm tra visibility hiệu lực (mục 3.2) + status
         3. tăng downloadCount
         4. ghi audit
         5. sinh presigned URL NỘI BỘ, hạn ngắn (~60s)
         6. trả 200 rỗng kèm:
              X-Accel-Redirect: /internal-minio/<path>?<chữ ký>
              Content-Disposition: attachment; filename="..."
   │
   ▼
Nginx    location /internal-minio/ { internal; proxy_pass <MinIO>; }
         → truyền file thẳng từ MinIO tới browser
```

Presigned URL **không bao giờ rời khỏi máy chủ** — trình duyệt chỉ thấy
`/api/v1/resources/{id}/file`. Nhờ vậy giữ được đầy đủ: kiểm tra quyền, MinIO
không public, takedown có hiệu lực ngay, counter chính xác, và Spring không
giữ thread trong lúc truyền file.

Khi triển khai phải giữ nguyên **URI, query chữ ký và Host** đúng với endpoint
đã dùng lúc ký, nếu không MinIO trả 403 vì sai chữ ký.

**Local** — không có nginx (Angular dev-server proxy thẳng vào Spring:8080).
`X-Accel-Redirect` sẽ bị trình duyệt bỏ qua và người dùng tải về file rỗng.

Vì vậy bắt buộc có cờ cấu hình:

```yaml
app:
  download:
    mode: ${APP_DOWNLOAD_MODE:stream}   # stream | accel
```

- `.env` (Local): `stream` — giữ `StreamingResponseBody` như hiện nay.
- `.env.demo` và production: `accel`.

Cả hai nhánh dùng **chung** bước 1–4. Chỉ khác cách trả file ở bước cuối.

### 8.4 Quyền tải — GUEST không được tải file

Xem và tải là **hai quyền khác nhau**. Bảng 3.3 áp cho việc *xem*; việc *tải*
chặt hơn một bậc:

| Hành động | GUEST | Đã đăng nhập |
|---|---|---|
| Xem danh sách, chi tiết tài nguyên `PUBLIC` | Có | Có |
| Xem preview (PDF, ảnh, video) của `PUBLIC` | Có | Có |
| Xem video YouTube nhúng | Có | Có |
| **Tải file về máy** | **Không → 401** | Theo bảng 3.3 |

GUEST gọi `GET /resources/{id}/file` nhận **401** với mã
`DOWNLOAD_REQUIRES_AUTH` (6011). Mã riêng thay vì `UNAUTHENTICATED` chung để FE
hiện đúng thông điệp *"Đăng nhập để tải tài liệu"* ngay tại nút tải, thay vì
đẩy người dùng sang trang đăng nhập như một lỗi phiên.

Tài nguyên `YOUTUBE` và `EXTERNAL_LINK` không đi qua endpoint này — GUEST vẫn
xem trực tiếp bình thường.

Lý do: chống bot tải hàng loạt, truy vết được ai đã tải, áp rate limit theo tài
khoản, và bảo vệ tài nguyên của trường.

#### Cách triển khai — đừng dùng `.authenticated()` cho endpoint này

`SecurityConfig:94` hiện có `GET /api/v1/resources/**` là `permitAll()`.
**Giữ nguyên.**

Nếu đổi endpoint tải sang `.authenticated()`, Spring Security sẽ chặn **trước
khi** request tới controller và trả `UNAUTHENTICATED` chung — FE không bao giờ
nhận được mã 6011, và nút tải không hiện đúng thông điệp.

Đúng cách: để `permitAll` ở tầng URL, rồi **controller/service tự kiểm tra**
`Principal == null` và ném `AppException(DOWNLOAD_REQUIRES_AUTH)`. Filter JWT
vẫn chạy trên request `permitAll`, nên nếu có token hợp lệ thì
`SecurityContext` vẫn được điền bình thường.

Hai trường hợp phải phân biệt được trong test:

| Tình huống | HTTP | Mã |
|---|---|---|
| GUEST (không có token) tải file | 401 | **6011** `DOWNLOAD_REQUIRES_AUTH` |
| Token hết hạn/không hợp lệ | 401 | Mã lỗi phiên thông thường (1011/1009) |

Hai ca này cùng trả 401 nhưng khác mã, vì FE xử lý khác nhau: ca đầu mời đăng
nhập tại chỗ, ca sau phải làm mới phiên hoặc đăng xuất.

#### Rủi ro đi kèm của `permitAll` diện rộng

`GET /api/v1/resources/**` permitAll nghĩa là **mọi endpoint GET mới thêm dưới
`/resources` đều công khai theo mặc định**, trừ khi người viết nhớ gắn
`@PreAuthorize`. Đây là kiểu cấu hình fail-open.

Hiện `/resources/me` an toàn nhờ có `@PreAuthorize("isAuthenticated()")` ở tầng
method. Nhưng khi Tuần 2 thêm các endpoint mới, phải:

- Gắn `@PreAuthorize` cho **mọi** endpoint GET không công khai dưới `/resources`.
- Có test khẳng định GUEST gọi từng endpoint đó nhận 401/403, không phải 200.

### 8.5 `Content-Disposition` cho tên file tiếng Việt

Tên file có dấu không đặt trực tiếp vào `filename=` được — header HTTP chỉ nhận
ASCII. Phải dùng **cả hai** tham số theo RFC 5987/6266:

```http
Content-Disposition: attachment;
    filename="Tai-lieu.pdf";
    filename*=UTF-8''T%C3%A0i%20li%E1%BB%87u.pdf
```

- `filename=` — bản ASCII dự phòng, đã bỏ dấu và thay khoảng trắng. Dành cho
  trình duyệt cũ không hiểu `filename*`.
- `filename*=` — bản thật, `UTF-8''` rồi percent-encode. Trình duyệt hiện đại
  ưu tiên tham số này.

Thứ tự bắt buộc: `filename` trước, `filename*` sau. Trình duyệt cũ đọc tham số
đầu và bỏ qua cái nó không hiểu.

Áp dụng cho **cả hai** chế độ `stream` và `accel`. Ở chế độ `accel`, header do
Spring đặt và nginx phải chuyển tiếp nguyên vẹn — nginx **không** được tự sinh
`Content-Disposition` từ MinIO đè lên.

### 8.6 Kiểm thử bắt buộc cho luồng download

Presigned URL nội bộ chứa chữ ký có ký tự đã percent-encode. Chỉ cần nginx làm
sai một khâu là MinIO trả 403, và lỗi này **không xuất hiện ở Local** vì Local
chạy chế độ `stream`. Bắt buộc test trên môi trường có nginx:

- [ ] Tên file chứa dấu tiếng Việt và khoảng trắng → tải đúng tên (§8.5)
- [ ] `filename*=UTF-8''…` tới được trình duyệt, nginx không đè bằng header của MinIO
- [ ] Chữ ký chứa ký tự `+`, `/`, `=` sau khi encode → nginx **không** decode lại
- [ ] Nginx giữ nguyên **path**, **query string** và **Host** đúng như lúc ký
- [ ] File > 100MB → truyền hết, không timeout, không đệm ra đĩa
- [ ] Presigned hết hạn (quá ~60s) → 403 từ MinIO, FE báo lỗi rõ ràng
- [ ] `X-Accel-Redirect` **không** lọt ra response mà trình duyệt thấy được
- [ ] Cùng một tài nguyên, `downloadCount` tăng đúng 1 cho mỗi lần tải
- [ ] GUEST gọi endpoint → **401 + code 6011**, và **không** sinh presigned URL nào
- [ ] Token hết hạn → **401 + mã lỗi phiên** (1011/1009), phân biệt được với 6011
- [ ] Tài nguyên `ARCHIVED` mà người gọi có quyền xem → **410 + code 6010**
- [ ] Tài nguyên `INTERNAL` mà GUEST gọi → **404**, không phải 401/403/410

---

## 9. Bản quyền

### 9.1 Xác nhận phải được lưu vết, không chỉ là checkbox trên FE

Một checkbox phía client không chứng minh được điều gì sau này. Lưu trên
`resources`:

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `copyrightDeclaration` | enum | Người upload khai nguồn gốc tài liệu |
| `copyrightConfirmedAt` | timestamp | Thời điểm xác nhận |
| `copyrightConfirmedBy` | FK `users` | Ai xác nhận |
| `copyrightTermsVersion` | varchar | Phiên bản điều khoản tại thời điểm xác nhận |

`copyrightTermsVersion` là trường quan trọng nhất và dễ bị bỏ quên: khi điều
khoản thay đổi, nó cho biết người dùng đã đồng ý với **bản nào**.

### 9.2 Giá trị của `copyrightDeclaration`

| Giá trị | Nghĩa |
|---|---|
| `OWN_WORK` | Tự soạn |
| `AUTHORIZED` | Được tác giả cho phép chia sẻ |
| `PUBLIC_DOMAIN` | Thuộc phạm vi công cộng |
| `HAS_PERMISSION` | Có giấy phép/thỏa thuận sử dụng |
| `EXTERNAL_SOURCE` | Lấy từ nguồn ngoài, có ghi nguồn |

Kèm theo `authorName`, `sourceName`, `sourceUrl`, `licenseType`.

Với `EXTERNAL_SOURCE` và `AUTHORIZED`, `sourceName` là **bắt buộc**.

### 9.3 Khi nào phải xác nhận lại

Đổi **file** hoặc **nguồn tài liệu** (`fileUrl`, link YouTube/ngoài,
`sourceName`, `sourceUrl`, `authorName`, `licenseType`) thì xác nhận bản quyền
cũ **không còn giá trị**. Teacher phải xác nhận lại, và `copyrightConfirmedAt`
được ghi mới.

Không xác nhận lại thì không gửi duyệt được — trả 400
`COPYRIGHT_NOT_CONFIRMED` (6012).

### 9.4 Giới hạn dữ liệu cá nhân

Audit log ghi **sự kiện gửi duyệt** (ai, lúc nào, tài nguyên nào, bản điều
khoản nào). Không thu thập thêm dữ liệu cá nhân ngoài mức cần thiết cho mục
đích này.

Không lưu thông tin cá nhân của trẻ trong học liệu — áp dụng cho **cả
production**, không riêng demo.

---

## 10. Báo cáo nội dung

- Mọi người đã đăng nhập đều báo cáo được tài nguyên vi phạm/sai nội dung.
- Admin có thể **ẩn ngay** tài nguyên bị báo cáo, trước khi xem xét.
- Khi tài nguyên bị gỡ, **phải thông báo cho người upload** kèm lý do.
- Tài nguyên bị gỡ chuyển `ARCHIVED` và trả 410 theo §4.3.

---

## 11. Những gì V1 cố ý không làm

VIP/thanh toán, quảng cáo, AI tóm tắt, hồ sơ mạng xã hội của người đăng, và
chuyển đổi DOCX/PPTX sang PDF trong request upload. Nếu làm preview Office thì
phải là worker riêng, không chạy trong luồng upload.
