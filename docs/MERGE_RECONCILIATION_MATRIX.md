# Ma trận hợp nhất `develop` ↔ `fix/senior-review-phase1`

Ngày lập: 2026-09-05 · `merge-base` `08f632c` · develop `7951792` · feature `2e11de5`

Hai nhánh giải **cùng một nghiệp vụ** (quyền xem tài nguyên) theo hai cách khác
nhau, sinh **34 hunk / ~646 dòng** conflict trên 7 file.

Nguyên tắc quyết: theo [`BUSINESS_RULES_V1.md`](BUSINESS_RULES_V1.md) và
[`API_CONTRACT_V2.md`](API_CONTRACT_V2.md). **Không chọn theo nhánh nào mới hơn.**
Ký hiệu: **D** = giữ develop · **F** = giữ feature · **K** = kết hợp.

---

## 0. Kết luận sớm — ba điều quan trọng nhất

1. **develop có hai endpoint mà feature không có**: `GET /me/favorites` và
   `GET /me/favorites/ids`. Đây chính là lỗ hổng khiến FE phải hiện "Chức năng
   đang hoàn thiện". **Phải mang sang** — nhưng đổi đường dẫn theo contract §4
   và bọc thêm lọc visibility.
2. **develop có `buildDownloadFileName` làm sạch tên file**; feature dùng
   `resource.getTitle()` thô. Đây là thứ feature **thiếu**, không phải thứ
   develop làm sai. Mang sang.
3. **develop vẫn dùng `topic.isActive` / `category.isActive`**. Feature đã migrate
   `is_active → visibility` (D1, `860d738`). Đếm thực tế trong `src/main/java`:

   | Nhánh | Số lần dùng `isActive` / `getIsActive` |
   |---|---|
   | `origin/develop` | **46** |
   | `fix/senior-review-phase1` | **0** |

   Đây là điểm hợp nhất tốn công nhất và nó **không nằm gọn trong 7 file
   conflict**: 46 chỗ trải khắp repo sẽ không compile sau khi hoà. Phải viết lại
   theo `visibility`, không phải bỏ đi. Lên lịch cho lát này riêng, đừng gộp.

---

## 1. `SecurityConfig.java` — 4 hunk

| # | Hạng mục | Quyết | Căn cứ |
|---|---|---|---|
| 1 | import CSRF | **F** | kéo theo quyết định #3 |
| 2 | import `Slf4j` / `IOException` | **K** | union, cả hai đều cần |
| 3 | `csrf(...)` | **F** | API_CONTRACT_V2 §—dòng 185: "JWT trong cookie HttpOnly. **CSRF qua cookie `XSRF-TOKEN`**". develop `csrf.disable()` với lý do "SameSite=Lax là đủ" — trái contract, và FE đã gắn `X-XSRF-TOKEN` (FE_CHANGELOG). Nếu muốn bỏ CSRF thì phải sửa contract trước, không sửa lén trong merge |
| 4 | `requestMatchers` | **F + 2 bổ sung** | xem dưới |

### 1.4 Matcher — giữ bản fail-closed của feature, vá 2 lỗ

develop dùng wildcard `/{tài nguyên}/**` permitAll → fail-open. Feature liệt kê
tường minh → fail-closed. Giữ feature. Nhưng khi liệt kê, feature **đánh rơi 2
route mà contract ghi là công khai**:

| Route | develop | feature | Contract | Việc phải làm |
|---|---|---|---|---|
| `GET /api/v1/comments` | permitAll (`/**`) | *không có* → rơi vào `anyRequest().authenticated()` | §6: **Công khai** | **Thêm** `.requestMatchers(GET, "/api/v1/comments").permitAll()` |
| `GET /api/v1/categories/{...}` | permitAll (`/**`) | chỉ `/api/v1/categories` | §—dòng 399 chỉ liệt kê route tập hợp | **Kiểm chứng bằng FE** trước khi kết luận: nếu Portal có gọi `/categories/{slug}` hay `/categories/{id}/topics` thì Guest sẽ 401 |

Route mới của develop dưới `/me/favorites` không khớp `/api/v1/resources/*`
(hai đoạn) nên rơi xuống `anyRequest().authenticated()` — đúng ý, không cần
matcher riêng. Đây là fail-closed hoạt động đúng.

> Không có test nào hiện bắt được hai lỗ trên. `ResourceSlugSecurityIntegrationTest`
> chỉ phủ route resource. Cần bổ sung case Guest gọi `GET /comments`.

---

## 2. `ResourceService.java` (interface) — 4 hunk

Tập method gần như trùng nhau. Khác biệt thật:

| Method | develop | feature | Quyết | Lý do |
|---|---|---|---|---|
| `getPortalResources` | `(filter, int page, int size)` | `(filter, Pageable, Viewer)` | **F** | `Viewer` là trục của tuần 2; `Pageable` theo `75730b5` (sort đã sanitize) |
| `getAdminResources` | `(filter, int, int)` | `(filter, Pageable)` | **F** | như trên |
| `getMyResources` | `(filter, int, int, username)` | `(filter, Pageable, username)` | **F** | như trên |
| `getResourceBySlug` | `(slug)` | `(slug, Viewer)` | **F** | guard cần Viewer |
| `getResourceFileInfo` | `(id)` | `(id, Viewer)` | **F** | Guest không được tải (contract §—dòng 235: 401 `DOWNLOAD_REQUIRES_AUTH`) |
| `getFavoriteResources` | `(int, int, username)` | **không có** | **K** | mang sang, đổi thành `(Pageable, username, Viewer)` — xem §2.1 |
| `getFavoriteResourceIds` | `(username)` | **không có** | **K** | mang sang, xem §2.1 |
| 15 method còn lại | — | — | — | **giống hệt**, không phải quyết gì |

### 2.1 Favorites — điểm dễ sai nhất

Contract §4 (dòng 325–334) nói rõ:

> `POST /resources/{id}/favorite` đã có ✅. Thiếu hai endpoint đọc — đây chính là
> lý do FE đang mock danh sách rỗng.
> FE hiện gọi `/me/favorites` và `/me/favorites/ids` — **phải đổi** sang
> `/api/v1/favorites` và `/api/v1/favorites/ids`.

develop hiện thực đúng chức năng nhưng **đặt ở đúng đường dẫn mà contract bảo
phải bỏ**. Vậy:

- Giữ **thân hàm** của develop.
- Chuyển path sang `/api/v1/favorites` và `/api/v1/favorites/ids` (controller
  riêng, không nằm dưới `/resources`).
- **Bổ sung lọc visibility**: một tài nguyên đã lưu có thể sau đó bị chuyển sang
  `PRIVATE` hoặc `ARCHIVED`. Trả nguyên vẹn danh sách đã lưu là **rò rỉ**. Phải
  đi qua `portalVisibleTo(viewer)` như list thường.
- FE `favorites.component.ts` bỏ nhánh "Chức năng đang hoàn thiện" **sau** khi
  BE lên; trước đó giữ nguyên.

---

## 3. `ResourceServiceImpl.java` — 13 hunk

| # | Vị trí | develop | feature | Quyết | Lý do |
|---|---|---|---|---|---|
| 1–2 | import | `PageableUtils`, `PageImpl` | `ResourceFileTypeRegistry` | **K** | union theo thân hàm cuối cùng |
| 3 | `getPortalResources` | spec nội tuyến: `PUBLIC` + `APPROVED` + cascade `isDeleted`/`isActive` | `ResourceVisibilitySpecifications.portalVisibleTo(viewer)` + projection | **F** | develop chỉ có một tầng `PUBLIC`; không có `INTERNAL`, không cho chủ sở hữu thấy `PRIVATE` của mình. BUSINESS_RULES §—phân tầng đòi 3 mức. Ngoài ra bản develop dùng `isActive` — đã bị D1 xoá |
| 4 | `getAdminResources` | `int page,size` | `Pageable` | **F** | |
| 5 | `getMyResources` | `PageableUtils.createPageable` | `Pageable` truyền vào | **F** | |
| 6 | thumbnail YouTube | — | thêm comment | **F** | chỉ là comment |
| 7 | `toggleFavorite` | `findByIdWithDetails` + `ensurePortalVisible` | `existsById` → 404 | **K** | develop **đúng hơn về nghiệp vụ** (không được lưu thứ mình không nhìn thấy); feature đúng hơn về mã lỗi. Kết hợp: `resourceAccessGuard.requireViewable(...)` — được cả 404 đúng lẫn kiểm tra quyền, và dùng mô hình 3 tầng thay vì `PUBLIC`-only |
| 8 | `getResourceBySlug` | đọc `SecurityContextHolder` ngay trong service, tự tính `privileged` | `resourceAccessGuard.requireViewable(...)`, `findBySlug` cố ý không lọc | **F** | develop trộn Spring Security vào tầng service — mỗi chỗ tự tính quyền là cách sinh ra lỗ hổng. Feature có ranh giới duy nhất (`ViewerResolver`) và thứ tự 404-trước-410 đã có test |
| 9 | `determineFileType` | có | đã tách ra registry | **F** | `ResourceFileTypeRegistryTest` phủ 3 case |
| 10 | `getResourceFileInfo` | tự tính `privileged`, không chặn Guest | `requireDownloadable(..., viewer)` | **F** | contract §—dòng 235: Guest → **401** `DOWNLOAD_REQUIRES_AUTH`. develop cho Guest tải |
| 11 | tên file tải về | `buildDownloadFileName` — lọc `\ / : * ? " < > \| CR LF`, fallback `resource-{id}` | `resource.getTitle()` thô | **D** | **feature thiếu**. Tiêu đề tiếng Việt có `/` hoặc `"` sẽ sinh tên file hỏng; tiêu đề rỗng cho ra `.bin`. Mang `buildDownloadFileName` sang, gọi nó với `ResourceFileTypeRegistry.normalizeExtension(...)` |
| 12 | `.fileName(...)` | `fileName` (đã gồm đuôi) | `fileName + "." + extension` | **D** | hệ quả của #11 |
| 13 | `getContentTypeByExtension` | switch nội tuyến | `ResourceFileTypeRegistry.contentTypeOrDefault` | **F** | cùng bảng ánh xạ, bản feature có test |

`createBaseSpecification` **giống hệt nhau** trên cả hai nhánh — không phải quyết.

---

## 4. `ResourceController.java` — 7 hunk

| Endpoint | develop | feature | Contract | Quyết |
|---|---|---|---|---|
| `POST /` upload | giống | giống | — | — |
| `GET /` portal list | `page,size` | `Pageable` + `Viewer` | — | **F** |
| `GET /me` | giống | giống | — | — |
| `GET /me/favorites` | **có** | không | §4: đổi thành `/api/v1/favorites` | **K** — mang sang, đổi path |
| `GET /me/favorites/ids` | **có** | không | §4: `/api/v1/favorites/ids` | **K** — như trên |
| `GET /{slug}` | không Viewer | có Viewer | — | **F** |
| view counter | `PUT /{id}/view` | `POST /{id}/view` | dòng 234: **POST** ✅ | **F** |
| download counter | `PUT /{id}/download` riêng | bỏ, đếm trong `/file` | contract không liệt kê endpoint này | **F** |
| `GET /{id}/file` | `ResponseEntity<?>`, buffer qua `InputStreamResource` | `StreamingResponseBody` | dòng 235: `stream \| accel` (BUSINESS_RULES §8.3) | **F** |
| `Content-Disposition` | `ContentDisposition.attachment().filename(name, UTF_8)` | như nhau | RFC 5987 | **F** (giống nhau; khác biệt thật nằm ở tên file — xem §3 #11) |
| `DELETE /bulk` | DELETE | `POST /bulk-delete` | dòng 242: **POST `/bulk-delete`** ✅ | **F** |
| `PUT /{id}/restore` | PUT | `PATCH /{id}/restore` | dòng 243: **PATCH** ✅ | **F** |
| `PATCH /bulk-restore` | giống | giống | dòng 244 ✅ | — |
| 5 endpoint còn lại | giống | giống | — | — |

---

## 5. `TopicRepository.java` — 3 hunk

Hai bên **thêm method khác nhau**, không sửa cùng method → về bản chất là union.
Nhưng develop viết query theo `isActive`, mà D1 đã xoá cột đó.

| Method | Nguồn | Quyết |
|---|---|---|
| `findByCategoryIdAndIsDeletedFalse` (thêm `AND t.category.isDeleted = false`) | develop | **D** — điều kiện cascade là đúng, giữ |
| `findByCategoryIdAndIsDeletedFalseAndIsActiveTrue` | develop | **K** — giữ ý nghĩa, viết lại theo `visibility` thay `isActive` |
| `findAllByIsDeletedFalse` (+cascade category) | develop | **D** |
| `findAllByIsDeletedFalseAndIsActiveTrue` | develop | **K** — như trên |
| `findByCategoryIdAndIsDeletedTrue`, `findAllByIsDeletedTrue` | feature | **F** — phục vụ màn hình thùng rác |
| `countActiveResourcesByTopicIds` | feature | **F** — thay `@Formula` đã bỏ; **xoá là mất toàn bộ phần tăng tốc `91b165a`** |
| `default findAll()` override | develop | **D** |

Dọn kèm: bản feature viết annotation dạng đầy đủ
(`@org.springframework.data.jpa.repository.Query`) — chuyển về import thường cho
khớp phần còn lại của file.

---

## 6. `TopicServiceImpl.java` — 2 hunk

| # | develop | feature | Quyết | Lý do |
|---|---|---|---|---|
| 1 | `cb.equal(root.get("isDeleted"), deleted)` | thêm cascade visibility topic→category theo `viewer` | **F** | phân tầng là mục tiêu tuần 2. Kiểm lại một điểm: `viewer.isAdmin() && deleted` khiến người không phải admin **không bao giờ** xem được bản ghi đã xoá — đúng ý, nhưng cần một test khẳng định rõ điều đó |
| 2 | message key theo `isActive` | message key theo `visibility` | **F** | `isActive` đã bị D1 xoá |

Hunk 1 của develop có thêm `resolveSlug` + kiểm tra `DUPLICATE_SLUG` khi đổi tên —
**đây là logic riêng, không xung đột ngữ nghĩa với visibility**. Giữ cả hai:
kiểm tra slug của develop **cộng** message key theo visibility của feature.

---

## 7. `CommentServiceImpl.java` — 1 hunk

| develop | feature | Quyết |
|---|---|---|
| `getCommentsByResourceId(String, int page, int size)`, có `findByIdWithDetails` + `ensureCommentable(resource)`, tự tạo `Pageable` | `getCommentsByResourceId(String, Pageable)` | **K** |

Kết hợp: chữ ký `(String resourceId, Pageable pageable)` của feature **cộng** phần
thân `ensureCommentable(...)` của develop. Bỏ `ensureCommentable` là mở đường cho
việc đọc bình luận của tài nguyên chưa duyệt / đã ẩn.

Đã kiểm: `ensureCommentable` ([CommentServiceImpl.java:105](../src/main/java/com/kindergarten/warehouse/service/impl/CommentServiceImpl.java#L105)
trên develop) tự kiểm `visibility != PUBLIC` và cascade qua `topic.isActive` /
`category.isActive`. Nghĩa là nó **lặp lại đúng khuyết điểm** của
`ensurePortalVisible`: chỉ một tầng `PUBLIC`, và dùng cột đã bị D1 xoá.

Vậy quyết cuối: giữ **ý định** (phải chặn bình luận trên tài nguyên không xem
được), bỏ **cách hiện thực**. Thay toàn bộ thân `ensureCommentable` bằng
`resourceAccessGuard.requireViewable(resource, viewer)` — cần truyền thêm
`Viewer` vào `getCommentsByResourceId`, giống `getResourceBySlug`.

---

## 8. Tổng hợp

45 điểm quyết (nhiều hơn 34 hunk vì một hunk có thể chứa vài method):

| File | F | D | K |
|---|---|---|---|
| `SecurityConfig` | 3 | 0 | 1 |
| `ResourceService` | 5 | 0 | 2 |
| `ResourceServiceImpl` | 9 | 2 | 2 |
| `ResourceController` | 7 | 0 | 2 |
| `TopicRepository` | 3 | 3 | 2 |
| `TopicServiceImpl` | 2 | 0 | 1 |
| `CommentServiceImpl` | 0 | 0 | 1 |
| **Tổng** | **29** | **5** | **11** |

Feature thắng ở **mô hình quyền** (3 tầng, một ranh giới security duy nhất,
matcher fail-closed, mã lỗi đúng contract) và **hiệu năng** (projection, bỏ
`@Formula`).

develop thắng ở **những chỗ feature chưa làm**: hai endpoint favorites, làm sạch
tên file tải về, cascade `category.isDeleted` trong `TopicRepository`, kiểm tra
`DUPLICATE_SLUG`, `ensureCommentable`.

**Không có điểm nào mà develop và feature làm cùng một việc và develop làm tốt
hơn.** Nhưng cũng không có điểm nào được phép bỏ develop mà không mất chức năng.

---

## 9. Việc phải làm sau khi hợp nhất

- [x] Viết lại mọi truy vấn `isActive` của develop theo `visibility` — grep
      `isActive` toàn repo sau merge, kỳ vọng **0 kết quả** trong `main/java`
- [x] Thêm matcher `GET /api/v1/comments` permitAll (§1.4)
- [x] Kiểm FE có gọi `/categories/{...}` không; các route đọc cần thiết đã được liệt kê tường minh (§1.4)
- [x] Favorites: đổi path theo contract §4 **và** bọc `portalVisibleTo`
- [x] Mang `buildDownloadFileName` vào luồng download của feature
- [x] Test mới: Guest `GET /comments` → 200; favorites không trả tài nguyên đã
      chuyển `PRIVATE`; tên file tải về với tiêu đề chứa `/` và `"`.
- [x] `mvn test` — **138/138** (thêm `PageableUtilsTest` 8 case và
      `ErrorCodeMessageKeyTest` 3 case)
- [ ] `mvn verify -Pintegration-test` — chờ CI có Docker thật xác nhận 20 test
      Testcontainers (đã chạy 20/20 trên MySQL 8.0.46 thật qua probe tạm, nhưng
      đó là DB có sẵn schema nên **không** chứng minh Flyway-from-empty)
- [ ] Đo lại k6 ngắn: xác nhận projection còn nguyên tác dụng sau khi hoà

---

## 10. Kiểm chứng end-to-end trên stack demo — 2026-09-06

Ba lát cuối (sort, view/download, xử lý lỗi) đã chạy thật trên demo với MySQL,
Redis và MinIO thật, dữ liệu tự dựng phủ đủ ba tầng visibility. Dữ liệu thử đã
được xoá sau khi đo.

| Kiểm chứng | Kỳ vọng | Kết quả |
|---|---|---|
| Khách gọi `GET /resources` | chỉ thấy PUBLIC | ✅ chỉ `smoke-public` |
| Admin gọi `GET /resources` | thấy cả 3 tầng | ✅ public + internal + private |
| Khách `GET /{slug}` INTERNAL | 404, không lộ tồn tại | ✅ 404 |
| Admin `GET /{slug}` INTERNAL | 200 | ✅ |
| Khách `GET /{id}/file` PUBLIC | **401** code 6011 | ✅ `Please sign in to download` |
| Khách `GET /{id}/file` INTERNAL | **404**, không phải 401 | ✅ code 6001 |
| Admin tải PUBLIC / INTERNAL / PRIVATE | 200 | ✅ cả ba |
| `POST /{id}/view` | 200 | ✅ (trước khi sửa: **405**) |
| Lặp lại `POST /{id}/view` | 429 rate limit | ✅ |
| `sort=viewsCount,desc` | 200 | ✅ |
| `sort=passwordHash` / `topicCount` | **400** 9001 | ✅ |
| `?sort=xyz` trên `/categories` | **400** | ✅ |

### Hai điểm quan trọng nhất

**Đếm lượt tải đã đúng 1:1.** Ba lần gọi `/file` cho
`kindergarten:downloads:smoke-public = 3`. Trước khi bỏ `PUT /{id}/download` thì
FE gọi cả hai endpoint nên cùng ba lượt đó sẽ ra **6**.

**`Content-Disposition` đủ hai tham số** theo §0.4, và tên file đã được làm
sạch. Tiêu đề `Bài giảng "Toán/Hình" lớp Lá` cho ra:

```
attachment; filename="=?UTF-8?Q?B=C3=A0i_gi=E1=BA=A3ng_=5FTo=C3=A1n=5FH=C3=ACnh=5F_l=E1=BB=9Bp_L=C3=A1.pdf?=";
            filename*=UTF-8''B%C3%A0i%20gi%E1%BA%A3ng%20_To%C3%A1n_H%C3%ACnh_%20l%E1%BB%9Bp%20L%C3%A1.pdf
```

Dấu `"` và `/` thành `_` — đây là `buildDownloadFileName` mang từ develop sang
(§3 #11) đang chạy. Nội dung tải về đúng 51 byte, `Content-Type: application/pdf`,
qua `StreamingResponseBody`.

### Còn nợ sau vòng này

Demo DB hiện **0 resource**. Bộ kiểm trên dựng dữ liệu tạm rồi xoá, nên chưa có
dữ liệu thường trực để đo hiệu năng hay để người khác thử tay.
