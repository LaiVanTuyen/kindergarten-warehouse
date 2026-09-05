# USER FLOWS V1 — Portal, Teacher, Admin

Trạng thái: **CHỐT** cho V1. Hoàn tất hạng mục cuối của Tuần 1.

Quy tắc nghiệp vụ: [BUSINESS_RULES_V1.md](BUSINESS_RULES_V1.md).
Endpoint: [API_CONTRACT_V2.md](API_CONTRACT_V2.md).

Tài liệu này mô tả **đường đi của người dùng**, không mô tả giao diện. Bố cục
màn hình thuộc về giai đoạn thiết kế; thứ tự bước và các nhánh rẽ ở đây là bắt
buộc.

---

## 1. Portal — Guest

### 1.1 Luồng chính

```
Trang chủ
  │ chọn nhóm tuổi / danh mục, hoặc gõ tìm kiếm
  ▼
Danh sách tài liệu  (filter lưu trên URL)
  │
  ▼
Chi tiết + preview
  │ bấm "Tải tài liệu"
  ▼
Yêu cầu đăng nhập          ← BUSINESS_RULES §8.4
  │ đăng nhập thành công
  ▼
Quay lại ĐÚNG tài liệu đang xem
  │
  ▼
Tự tiếp tục tải, hoặc hiện lại nút tải
```

Guest **xem và preview** được tài nguyên `PUBLIC`, nhưng **không tải**. Video
YouTube và liên kết ngoài vẫn xem trực tiếp bình thường vì không đi qua endpoint
tải.

### 1.2 Bốn nhánh rẽ theo trạng thái tài nguyên

| Tình huống | Hành vi | Mã |
|---|---|---|
| `PUBLIC` đã duyệt | Xem được, preview được | 200 |
| `INTERNAL` | Chuyển tới đăng nhập; sau đăng nhập **quay lại đúng tài liệu** | 401 |
| `PRIVATE` không có quyền | Trang 404 thường | 404 |
| `ARCHIVED` từng public | Trang **"Tài liệu không còn được cung cấp"** | 410 |

`PRIVATE` và "chưa từng tồn tại" phải trả **cùng một trang 404**. Phân biệt hai
thứ này là làm lộ sự tồn tại của tài liệu — xem BUSINESS_RULES §4.3.

### 1.3 Quay lại sau đăng nhập

Ràng buộc bắt buộc, áp cho **mọi** điểm vào đăng nhập:

- Lưu URL đầy đủ đang xem, **gồm cả query filter**, trước khi chuyển sang login.
- Sau khi đăng nhập, quay lại đúng URL đó — không đưa về trang chủ.
- Nếu người dùng tới login vì bấm nút tải, sau khi quay lại thì **tiếp tục tải
  luôn** hoặc hiển thị lại nút tải ở trạng thái sẵn sàng.
- Đích quay lại chỉ nhận **đường dẫn nội bộ**. Không bao giờ redirect sang URL
  tuyệt đối lấy từ query — đó là lỗ hổng open redirect.

---

## 2. Portal — User / Phụ huynh

```
Đăng nhập
  ▼
Tìm / xem tài liệu
  ▼
Tải  ·  Lưu yêu thích
  ▼
Đánh giá   (1 lần, sửa được)
  ▼
Bình luận  (nhiều lần)
  ▼
Báo cáo nếu nội dung có vấn đề
```

**Rating và bình luận độc lập về nghiệp vụ, dữ liệu và API** — nhưng **không**
bắt buộc tách rời trên giao diện.

Giao diện **được phép** đặt "đánh giá" và "nhận xét" cạnh nhau trong cùng một
khu vực, vì đó là trải nghiệm tự nhiên. Điều kiện là bốn ràng buộc sau phải giữ
nguyên:

| Ràng buộc | Nghĩa |
|---|---|
| Request riêng | Rating gọi `PUT /resources/{id}/rating`; comment gọi `POST /comments`. Không có endpoint gộp |
| Gửi lẻ được | Người dùng chỉ đánh giá, hoặc chỉ bình luận, đều hợp lệ |
| Không rollback chéo | Một request lỗi **không** làm hỏng request còn lại — báo lỗi đúng phần đó, giữ phần đã thành công |
| Xoá độc lập | Xoá bình luận **không** làm mất đánh giá |

Thêm: mỗi người **một** đánh giá trên một tài liệu, sửa được điểm.

Điều phải tránh là **một endpoint gộp** nhận cả nội dung lẫn điểm — vì như vậy
sẽ tái tạo đúng mô hình dữ liệu sai mà V1 đang gỡ bỏ
([MIGRATION_RATINGS_DESIGN.md](MIGRATION_RATINGS_DESIGN.md)). Đặt chung khu vực
hiển thị thì không sao; gộp chung một lần ghi thì không được.

---

## 3. Teacher

### 3.1 Tạo tài liệu — wizard, không phải form dài

```
Tài liệu của tôi
  │ Tạo tài liệu
  ▼
1. Chọn nguồn      FILE | YOUTUBE | EXTERNAL_LINK
  ▼
2. Nhập nội dung   tiêu đề, mô tả, mục tiêu, điểm nổi bật
  ▼
3. Phân loại       danh mục, chủ đề, nhóm tuổi, loại học liệu,
                   lĩnh vực, phương pháp, đối tượng, thẻ
  ▼
4. Ảnh đại diện / preview
  ▼
5. Xem lại         hiển thị đúng như người dùng sẽ thấy
  ▼
6. Lưu nháp  ·  Gửi duyệt
```

Bước 6 có **hai** lối ra. "Lưu nháp" phải dùng được ở **bất kỳ bước nào**, không
chỉ bước cuối — nếu không, người dùng mất hết khi bỏ dở.

### 3.1.1 Hệ quả bắt buộc: API draft phải nhận dữ liệu chưa đầy đủ

"Lưu nháp ở mọi bước" không phải yêu cầu giao diện — nó là **ràng buộc API**.
Hợp đồng phải là:

```
POST  /api/v1/resources/draft        → tạo draft tối thiểu, trả resourceId
PATCH /api/v1/resources/{id}         → lưu từng bước, mọi trường đều optional
POST  /api/v1/resources/{id}/submit  → CHỈ ở đây mới validate toàn bộ
```

Toàn bộ validate trường bắt buộc dồn về `submit` (BUSINESS_RULES §6.1), gồm cả
xác nhận bản quyền còn hiệu lực (§9.3). Thiếu bản quyền thì đưa thẳng về bước
xác nhận, không bắt người dùng dò cả form.

**Hai chỗ trong code hiện tại chặn flow này:**

| Vị trí | Vấn đề |
|---|---|
| `ResourceCreationRequest` | `title` có `@NotBlank`, `topicId` có `@NotNull` → không tạo được draft rỗng |
| `ResourceServiceImpl:481` | Non-admin sửa tài liệu là **luôn** bị ép `setStatus(PENDING)` |

Chỗ thứ hai tinh vi hơn và dễ bỏ sót. Logic hiện tại đúng cho tài liệu đã duyệt
("ZERO TRUST": uploader sửa nội dung thì phải duyệt lại), nhưng khi có `DRAFT`
nó sẽ khiến **mỗi lần lưu nháp lại vô tình gửi duyệt**. Quy tắc mới:

- Đang `DRAFT` → sửa vẫn giữ `DRAFT`. Chỉ `submit` mới chuyển sang `PENDING`.
- Đang `APPROVED`/`REJECTED` → giữ nguyên hành vi hiện tại (về `PENDING`).

Nếu không sửa cả hai chỗ, wizard chỉ chạy được như một form dài trá hình.

### 3.2 Theo dõi qua tab

```
Nháp | Chờ duyệt | Đã duyệt | Bị từ chối | Lưu trữ
```

| Tab | Hành vi bắt buộc |
|---|---|
| Bị từ chối | Hiện **lý do từ chối** ngay trên thẻ, không giấu sau một cú bấm → sửa → gửi lại |
| Đã duyệt | Sửa nội dung phải **cảnh báo trước khi lưu**: *"Tài liệu sẽ quay lại trạng thái chờ duyệt"* |
| Đã duyệt | Đổi **riêng** visibility thì **không** cảnh báo — không cần duyệt lại |

Danh sách trường nào kích hoạt cảnh báo: BUSINESS_RULES §5.2. Chỉ `visibility`
được miễn.

### 3.3 Giới hạn quyền

Teacher **không** thao tác được tài liệu của người khác. Ràng buộc này phải nằm
ở **service**, không chỉ ẩn nút trên giao diện. Ẩn nút là trải nghiệm; kiểm tra
ở service mới là bảo mật.

Kèm theo: không hiển thị nút chức năng mà vai trò hiện tại không được phép dùng.

---

## 4. Admin

### 4.1 Luồng ưu tiên — kiểm duyệt

```
Dashboard
  ▼
Hàng đợi kiểm duyệt
  ▼
Mở preview  (ngay trong drawer/modal, không rời trang)
  ▼
Kiểm tra metadata + bản quyền
  ▼
Approve  ·  Reject kèm lý do
  ▼
Chuyển sang tài liệu tiếp theo  (không quay về danh sách)
```

Tiêu chí thiết kế: **duyệt liên tiếp nhiều tài liệu mà không phải chuyển trang**.
Mỗi lần quay về danh sách rồi mở lại là một lần mất ngữ cảnh.

### 4.2 Luồng xử lý báo cáo

```
Danh sách báo cáo
  ▼
Xem tài liệu + lịch sử kiểm duyệt
  ▼
Ẩn tạm thời          ← được phép làm ngay, trước khi kết luận
  ▼
Giữ nguyên  ·  Archive  ·  Gỡ
  ▼
Nhập lý do
  ▼
Thông báo người đăng
```

Bước "ẩn tạm thời" đứng **trước** bước kết luận là có chủ đích: admin cần chặn
nội dung có vấn đề ngay, rồi mới xem xét.

Bước thông báo người đăng là **bắt buộc**, không phải tuỳ chọn
(BUSINESS_RULES §10).

### 4.3 Các nhánh quản trị còn lại

Người dùng và phân quyền · Danh mục/chủ đề/taxonomy · Banner · Audit log ·
Dashboard thống kê thật.

Dashboard hiện dùng mock. Cho tới khi có ba endpoint thật
(API_CONTRACT_V2 §12), màn này **phải gắn nhãn "dữ liệu mẫu"** — không để người
xem demo hiểu nhầm là số liệu thật.

Audit log phải đọc được như nhật ký nghiệp vụ, không phải log kỹ thuật: ai, làm
gì, trên tài liệu nào, lúc nào.

---

## 5. Nguyên tắc UX chốt

### 5.1 Điều hướng và nội dung

- **Tìm kiếm** và **nhóm tuổi** là hai đường vào chính của Portal. Mọi thiết kế
  trang chủ phải đặt hai thứ này ở vị trí nổi bật nhất.
- Portal ưu tiên nội dung; tránh làm Portal trông như trang quản trị.
- Admin ưu tiên tốc độ xử lý hàng đợi.
- Teacher upload theo wizard, **không** dùng một form dài.

### 5.2 Trạng thái và URL

- Mọi thao tác ghi phải có đủ **loading**, **success**, **error** — hiển thị lỗi
  thật từ backend, không nuốt thành thông báo chung chung.
- Filter lưu trên URL, để chia sẻ và tải lại trang đều giữ nguyên kết quả.
- Quay lại từ login giữ nguyên trang đang xem (§1.3).
- Empty state phải nói rõ bước tiếp theo, không chỉ hiện "Không có dữ liệu".

### 5.3 Giao diện

- Mobile và tablet là **yêu cầu bắt buộc**, không phải cải tiến sau.
- Thân thiện nhưng không trẻ con — người dùng là giáo viên và phụ huynh, không
  phải trẻ trực tiếp dùng.
- Tương phản đạt WCAG AA. Font hỗ trợ tiếng Việt đầy đủ.
- Nút lớn, dễ thao tác trên tablet.
- Hạn chế gradient và animation.

### 5.4 Về việc tham khảo Tailieu.vn

Chỉ tham khảo **cấu trúc thông tin**: cách tổ chức kho tài liệu, bố cục trang
chi tiết, khối "tài liệu liên quan", cách trình bày preview.

Không lấy: mô hình VIP/thanh toán, quảng cáo, giao diện, hay nội dung tài liệu
của họ. Dữ liệu mẫu cho demo phải **tự tạo**.

---

## 6. Điều kiện coi là hoàn thành từng luồng

- [ ] Guest: đủ 4 nhánh ở §1.2 trả đúng mã và đúng trang
- [ ] Guest: bấm tải → login → quay lại **đúng** tài liệu, giữ nguyên filter
- [ ] User: đánh giá và bình luận độc lập được; xoá bình luận không mất đánh giá
- [ ] Teacher: lưu nháp được ở **mọi** bước của wizard
- [ ] Teacher: sửa tài liệu đã duyệt hiện cảnh báo; đổi riêng visibility thì không
- [ ] Teacher: không thao tác được tài liệu người khác — chặn ở **service**
- [ ] Admin: duyệt liên tiếp nhiều tài liệu không phải rời trang
- [ ] Admin: ẩn tạm thời được trước khi kết luận; người đăng nhận thông báo
- [ ] Toàn bộ: filter trên URL, có loading/success/error, chạy được trên mobile
