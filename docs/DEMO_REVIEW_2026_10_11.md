# Báo cáo demo FE/BE — 11/10/2026

> Cập nhật: xem [báo cáo kiểm tra lại Admin/Portal](ADMIN_PORTAL_RECHECK_2026_10_11.md). Các ghi chú Dashboard mock trong báo cáo cũ đã được thay thế sau đợt sửa tiếp theo.

## Kết quả hiện tại

Docker đã chạy. Môi trường warehouse_demo đang hoạt động với backend Spring Boot, MySQL, Redis, MinIO. Hai giao diện Angular đã build lại và được phục vụ trên localhost bằng máy chủ Node có proxy cùng origin.

- Portal: http://localhost:4301
- Admin: http://localhost:4300
- Giáo viên demo: demo.teacher@example.com / (mật khẩu: biến DEMO_TEACHER_PASSWORD trong .env.demo)
- Quản trị demo: demo.admin@example.com / (mật khẩu: biến DEMO_ADMIN_PASSWORD trong .env.demo)

Hai tài khoản trên chỉ được tạo trong database demo riêng. Tài khoản seed và mật khẩu từ .env.demo không được đưa vào báo cáo. Không mở tunnel công khai.

## Bằng chứng kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Build Portal + Admin cấu hình demo | Thành công sau các sửa FE |
| Backend Maven package và test thường/security | 160/160 đạt trong lần kiểm tra trước cùng phiên ngày 11/10 |
| MySQL thật/Testcontainers | 20/20 đạt, không skip; Flyway và Hibernate validation chạy thật |
| Nghiệp vụ qua HTTP, lưu dữ liệu thật | 62/62 bước đạt |
| Giao diện Chrome headless | 20 bước/màn đạt, 54 API calls, không lỗi JavaScript |
| PDF/Word/PowerPoint/Excel qua HTTP | READY: PDF 3 trang, DOCX 3, PPTX 2, XLSX 1 |
| Quyền xem trước | Guest công khai 200; tệp gốc 401; tài liệu riêng tư 404; chủ tài liệu/admin xem được |
| Bộ đếm tải | Xem trước không tăng lượt tải; tải tệp gốc thành công tăng một lượt |
| Mobile | Chi tiết tài liệu ở 390px không tràn ngang; trước đó đã kiểm tra trang chủ và login admin |

Có một phản hồi 429 cho POST /resources/{id}/view trong kiểm tra giao diện: mở lại cùng tài liệu từ cùng IP trong 60 giây bị giới hạn theo RateLimitingAspect. Đây là phản hồi dự kiến; việc đọc và thao tác tài liệu vẫn đạt.

Integration test lần đầu không đạt do MySQL không khởi động kịp lúc Docker đang biên dịch nhiều dịch vụ. Đã tăng thời gian chờ khởi động/kết nối lên 300 giây, giữ nguyên các assertions. Lần chạy lại đạt 20/20.

## Nghiệp vụ đã chạy thật

| Nghiệp vụ | Mức kiểm chứng |
|---|---|
| Đăng nhập giáo viên/admin, giữ phiên khi reload, đăng xuất | API và giao diện; admin demo được đăng nhập và kiểm tra quyền riêng |
| Danh sách học liệu, tìm kiếm và chi tiết | Giao diện tải API thật; tìm kiếm có học liệu mẫu |
| Giáo viên upload PDF → PENDING → admin duyệt → APPROVED | API và thao tác từ giao diện đều đạt |
| Từ chối học liệu | API đạt; học liệu bị từ chối không lộ cho guest |
| Tài liệu của tôi, yêu thích, hồ sơ | Giao diện và API |
| Xem trước PDF/DOCX/PPTX/XLSX | Chuyển đổi thật bằng PDFBox/LibreOffice; đọc PDF từ giao diện |
| Tải tệp gốc, chặn guest, bộ đếm tải | API và storage thật |
| Thêm/bỏ yêu thích | API, lưu danh sách và thao tác giao diện |
| Bình luận kèm 5 sao | API và gửi/hiển thị từ giao diện |
| PUBLIC/PRIVATE | API, guest metadata/page bị chặn; owner xem được; đã khôi phục PUBLIC |
| Danh mục/chủ đề: tạo, sửa, xóa mềm, khôi phục | API; màn quản lý đọc dữ liệu thật |
| Người dùng: tạo, khóa/mở khóa, xóa mềm, khôi phục | API; màn quản lý đọc dữ liệu thật |
| Banner bật/tắt | API; màn quản lý đọc dữ liệu thật |
| Khôi phục tài liệu | API |
| Nhật ký | API và màn admin đọc log thật |

Không dùng kết quả này để khẳng định đã nghiệm thu mọi tổ hợp vai trò, mọi loại upload hoặc mọi thao tác bulk. Bộ integration kiểm tra visibility/favorites; các màn bulk chưa được thao tác đầy đủ trong browser lần này.

## Những sửa lỗi trong phiên

1. ResourcePreviewService: đọc lại manifest sau khi chiếm khóa, tránh tạo trùng bản xem trước khi worker vừa hoàn tất.
2. UserService FE: khóa và khôi phục gửi PUT đúng UserController hiện tại; ResourceService FE khôi phục gửi PUT đúng ResourceController. Một số bảng trong API_CONTRACT_V2 còn ghi PATCH, cần cập nhật tài liệu hợp đồng nếu giữ backend hiện tại.
3. Form upload: đồng bộ thông báo và bộ chọn tệp từ 100 MB về 50 MB theo backend.
4. CommentService FE: gửi tham số resourceId/content/rating đúng CommentController, chuẩn hóa phản hồi phẳng để bình luận mới hiển thị đúng cấu trúc user trên giao diện.
5. Script demo local: CORS cho localhost/127.0.0.1, cookie phù hợp HTTP local; dùng API age-groups để kiểm tra sẵn sàng vì actuator cần đăng nhập.
6. Test MySQL: thời gian chờ 300 giây phù hợp lần khởi động đầu trên Docker Desktop.

## Phần còn thiếu hoặc chưa xác minh

- Dashboard admin vẫn dùng DashboardService mock in-memory; số liệu và hành động trong dashboard chưa được coi là thống kê/duyệt thật. Duyệt thật thực hiện ở trang Tài nguyên.
- MAIL_USERNAME demo trống: gửi email xác thực/quên mật khẩu thật chưa sẵn sàng.
- Chưa nghiệm thu upload audio/video/YouTube, thao tác bulk, đổi avatar/mật khẩu, export log và mọi tổ hợp INTERNAL/DRAFT trên UI trong lần này.
- Ảnh avatar mặc định của form bình luận trỏ tới assets/images/default-avatar.png đang thiếu; có biểu tượng ảnh lỗi khi người dùng chưa đặt avatar.
- Thời gian tương đối trên admin có lệch khoảng 7 giờ do timestamp không mang múi giờ; cần chuẩn hóa UTC/offset giữa FE và BE.
- Portal initial bundle còn cảnh báo dung lượng 843.71 kB so với mức cảnh báo 500 kB. Build vẫn đạt.
- Chỉ xác minh local; chưa triển khai tunnel/VPS hoặc kiểm tra từ thiết bị khác.

## Vận hành

Chạy lại: scripts/start-local-demo.ps1 trong backend. Dữ liệu nằm trong volumes của project warehouse_demo và không dùng chung database phát triển.

Máy chủ FE: kindergarten-warehouse-angular/scratch/demo-server.cjs. Hai cổng 4300/4301 chỉ bind 127.0.0.1; proxy backend 8081, storage 9010. Nếu frontend server bị tắt, script khởi động lại khi hai cổng chưa có listener.

Script/API artifacts:
- scripts/demo-acceptance.cjs → target/demo-acceptance.json
- scripts/test-preview.ps1 → target/demo-preview-acceptance.log
- scripts/prepare-demo-accounts.ps1 → tạo/kiểm tra admin demo và xóa mềm chỉ những fixture trùng do acceptance retries
- target/demo-integration-retry.log → 20/20 integration tests
- kindergarten-warehouse-angular/scratch/demo-business-review.cjs → scratch/demo-review/business-results.json và screenshots

Một số dữ liệu demo được giữ lại để người dùng xem: tài liệu PDF/Office công khai, tài liệu từ chối, tài liệu tạo từ giao diện, danh mục/chủ đề và các tài khoản kiểm thử. Tất cả được tạo cho demo, không chứa thông tin thật của trẻ.

