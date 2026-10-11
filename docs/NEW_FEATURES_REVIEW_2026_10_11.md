# Review tính năng mới — 11/10/2026

Phạm vi: mã mới ở Backend/Portal/Admin cho related, tag/search/sort, tác giả, lịch sử tải, báo cáo vi phạm và thông báo. Không sửa mã nghiệp vụ, không commit. Dùng git -c safe.directory cho đúng hai repo, không thay cấu hình Git toàn máy.

## Findings

### 1. [P1] Chủ tài liệu có thể công khai lại tài liệu bị ẩn do vi phạm

ResourceReportService.java:88-90 chuyển visibility sang PRIVATE nhưng giữ trạng thái APPROVED. ResourceServiceImpl.updateVisibility:718-726 cho chủ sở hữu đổi visibility trực tiếp, không kiểm tra quyết định vi phạm và không đưa về PENDING. Sau khi admin RESOLVE + hideResource=true, tác giả gọi PATCH /resources/{id}/visibility với PUBLIC thì tài liệu lại xuất hiện cho khách dù báo cáo vẫn RESOLVED.

Cần trạng thái/cờ gỡ do kiểm duyệt mà tác giả không được tự bỏ; hoặc một quy trình gửi duyệt lại và admin khôi phục. Không nên dùng riêng visibility cho quyết định vi phạm. Test hiện tại chỉ khẳng định resource trở thành PRIVATE, chưa thử chủ sở hữu mở lại.

### 2. [P2] Tag khác dấu có thể làm lưu tài liệu lỗi trùng khóa

TagUtils.java:34-44 loại trùng bằng LinkedHashSet theo so sánh chuỗi Java. V24:11-16 dùng PK(resource_id,tag) theo collation mặc định utf8mb4_0900_ai_ci. Vì vậy Java giữ cả "bà" và "ba", còn MySQL coi chúng là cùng khóa. Khi lưu bộ tag này, INSERT vào resource_tags lỗi 1062 và giao dịch tạo/cập nhật tài liệu thất bại.

Đã tái hiện trên MySQL demo bằng TEMPORARY TABLE cùng PK/collation, với UTF-8 hex chính xác: ERROR 1062 Duplicate entry 'review-resource-ba'. Bảng tạm tự mất khi kết nối đóng, không thay dữ liệu ứng dụng.

Cần thống nhất phép loại trùng và collation lưu trữ. Bổ sung test MySQL cho cặp có/không dấu và cập nhật tag đã tồn tại; không chỉ test Set trong Java.

### 3. [P2] Hai yêu cầu đồng thời tạo được hai báo cáo OPEN

ResourceReportService.java:44-57 kiểm exists rồi insert trong giao dịch riêng. V24 resource_reports không có unique constraint cho báo cáo đang mở. Hai request từ hai tab đều có thể đọc exists=false trước khi một request commit, rồi cả hai lưu thành công. Điều này vi phạm quy tắc mỗi người chỉ có một báo cáo đang chờ trên mỗi tài liệu.

Cần ràng buộc DB cho trạng thái OPEN (ví dụ generated nullable open marker + unique key) và xử lý xung đột; hoặc khóa một bản ghi ổn định khi kiểm tra và tạo. Test hiện tại mock exists=true chỉ kiểm trường hợp tuần tự.

### 4. [P2] Hai admin xử lý cùng báo cáo có thể ghi đè và gửi thông báo trái ngược

ResourceReportService.java:72-86 tải report OPEN rồi cập nhật; ResourceReportRepository.findByIdWithDetails không khóa, ResourceReport không có @Version. Hai admin cùng đọc OPEN, một RESOLVE/ẩn tài liệu, một DISMISS: cả hai có thể trả thành công, gửi hai thông báo trái ngược, và kết quả lưu cuối có thể DISMISSED trong khi tài liệu đã bị ẩn.

Cần pessimistic lock, optimistic version hoặc cập nhật có điều kiện WHERE status=OPEN và chỉ thực hiện tác động/notify khi cập nhật thành công. Thêm test hai transaction thật, không chỉ test report đã có trạng thái đóng trước lúc gọi.

### 5. [P2] Lịch sử tải phân trang trước khi lọc quyền hiển thị

PortalDiscoveryService.java:92-102 lấy Page của toàn bộ download_history rồi loại tài liệu không còn hiển thị trong bộ nhớ, nhưng giữ history.getTotalElements(). Nếu 10 lượt gần nhất đều thuộc tài liệu đã gỡ còn lượt thứ 11 vẫn công khai, trang đầu trả rỗng và Portal báo "Chưa tải tài liệu nào", trong khi trang sau có dữ liệu; tổng số trang cũng tính cả tài liệu ẩn.

Cần áp visibility trong truy vấn trước pagination và count, giống ResourceVisibilitySpecifications. Test hiện tại chỉ có một tài liệu hiện và một tài liệu pending trong cùng trang, không khẳng định tổng/phân trang sau lọc.

## Điểm cần bổ sung nhỏ

- NotificationsComponent:55-57 đánh dấu đã đọc và giảm badge ngay cả khi API thất bại, error bị bỏ qua; nên cập nhật khi API thành công hoặc rollback/re-fetch khi lỗi.
- PortalDiscoveryService:55 vẫn fallback fullName sang username. Với dữ liệu cũ có fullName=null, API tác giả vẫn lộ username dù không có trường tên username trong DTO.
- Số lượt xem/tải của trang tác giả chỉ cộng DB, còn danh sách tài liệu cộng cả Redis chưa đồng bộ; hai số có thể lệch tạm thời.
- API_CONTRACT_V2.md chưa phản ánh endpoint mới; không thể dùng làm hợp đồng chính xác cho các tính năng này.

## Kiểm tra đã thực hiện trong lượt review

- Đọc và đối chiếu service/controller/repository/migration/entity, các component mới và payload FE; xem test để xác định phạm vi bao phủ.
- Chạy lại ResourceReportServiceTest, TagUtilsTest, ResourceSlugSecurityIntegrationTest: 29/29 đạt, không skip. Log: target/review-new-features.log.
- Tái hiện lỗi tag bằng MySQL thật như trên.
- Các race condition được xác định từ code/schema thiếu khóa/ràng buộc; chưa chạy stress test đồng thời trong lượt này.
- Không chạy lại toàn bộ 176 unit/28 integration/build FE; các con số đó là báo cáo đầu vào của người dùng, không phải kết quả mới của lượt review.

Các test đang xanh không phủ các tình huống nêu trong findings. Ưu tiên xử lý mục 1 trước khi dùng luồng xác nhận vi phạm để gỡ tài liệu thật.
