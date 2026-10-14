# Review, sửa lỗi và trình chiếu kế hoạch — 11/10/2026

Đã review Backend/Portal cho kế hoạch hoạt động, sửa các lỗi tìm thấy, bổ sung màn sử dụng trong lớp và triển khai vào warehouse_demo riêng. Không commit, không thay dữ liệu production.

## Sửa sau review

1. Tab Của tôi trước đây không nhận keyword và không có ô tìm kiếm. Đã bổ sung tìm tên/mục tiêu ở SQL trước pagination; cập nhật API và giao diện.
2. Đồng nghiệp đọc kế hoạch chia sẻ trước đây nhận cả reflection/taughtOn của chủ. Đã ẩn hai trường này với đồng nghiệp; chủ và ADMIN vẫn đọc được. Sao chép tiếp tục bỏ ghi nhận/ngày dạy.
3. Thay đổi nhập trong lúc lưu có thể bị đánh dấu đã lưu. Trình soạn hiện so sánh snapshot gửi đi với nội dung hiện tại; cập nhật version nhưng vẫn giữ dirty khi có sửa thêm, không tự chuyển sang trang xem. Chặn gửi lặp trong lúc lưu.
4. Có cảnh báo khi rời trang, tải lại hoặc đóng tab nếu chưa lưu; rời trang trong lúc lưu được chặn. Khi xung đột, nội dung vẫn giữ trên màn hình.
5. Hộp gắn học liệu trước đây giữ kết quả cũ và có thể bị request cũ ghi đè. Đã xóa kết quả khi tìm mới và hủy request khi tìm/chuyển nguồn; hủy khi đóng component.
6. API nhận steps:[null] trước đây có thể lỗi 500. Thêm validation từng phần tử, trả 400 trước service.

## Chế độ hoạt động

- Nút Bắt đầu hoạt động trên trang xem kế hoạch, route /plans/{id}/present, vẫn chỉ TEACHER/ADMIN và quyền kế hoạch hiện có.
- Màn chuẩn bị tổng hợp học liệu từng bước; báo tài liệu không còn khả dụng; đọc trạng thái preview PDF/Office; mở thử học liệu.
- YouTube dùng link/host/ID đã kiểm tra, iframe origin/referrer đúng, có mở trực tiếp YouTube. Giáo viên phát thử rồi xác nhận. Không có kiểm tra tự động bảo đảm mọi video nhúng/phát được.
- Khi bắt đầu, gọi lại GET chi tiết để kiểm quyền học liệu hiện tại. Không mở bản nguồn khi available=false.
- Chọn bước, trước/tiếp, chọn từng học liệu, toàn màn hình; PDF/Office dùng reader nội bộ, ảnh/video/audio dùng API tệp có kiểm quyền.
- Đồng hồ theo số phút từng bước: chạy/tạm dừng/đặt lại. Deadline tuyệt đối tránh bị chậm khi callbacks bị tạm ngưng. Chuyển bước reset và dừng đồng hồ; hết giờ không tự chuyển bước.
- Ghi chú giáo viên mặc định ẩn, có nút hiện/ẩn. Phím tắt khi không focus điều khiển: trái/phải chuyển bước, Space đồng hồ, N ghi chú.
- Khung học liệu điều chỉnh theo chiều cao màn hình; mobile 390px không tràn ngang.
- Player/iframe được tháo khi chuyển học liệu hoặc bước để không phát tiếp tài liệu cũ.

## Kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| Backend Maven package | 178/178 test thường/security đạt, không skip; target/plans-build.log |
| MySQL 8.0.46/Testcontainers | 46/46 đạt, không skip; target/plans-integration.log và target/plans-failsafe-reports |
| FE Jest | Portal 32, Admin 3, data-access 14 đạt |
| Build demo Portal + Admin | Đạt; cảnh báo bundle có sẵn vẫn còn |
| Chrome thao tác thật | 15 bước, 98 API calls đạt, không lỗi JavaScript hoặc phản hồi lỗi ngoài các ca thử quyền/validation có chủ đích; scratch/plans-audit/results.json |
| Bản in | PDF thật 2 trang A4, đã render và kiểm tra chữ/bố cục; không header/footer website |

Các ca trình duyệt: tạo 4 bước mặc định, soạn/lưu/gắn YouTube qua picker, quyền guest/đồng nghiệp, ẩn ghi nhận cá nhân, sao chép, hai tab với bản cũ 409 và giữ nội dung, cảnh báo rời trang, in, tạo từ bộ, mẫu admin, tìm mục tiêu của tôi, phát YouTube hơn 2 giây, fullscreen, đồng hồ, ẩn/hiện ghi chú, chuyển sang PDF và mobile.

MySQL test chạy bằng Maven container với api.version=1.44. Bind mount ổ G lỗi ở Docker Desktop nên đã chép pom/target đã compile vào container thử riêng; đã xuất báo cáo rồi xóa container thử. Test kiểm tra thật Flyway và Hibernate, không bỏ validation hay test. Ca privacy/search mới nằm trong LessonPlanIT; validation null mới nằm trong unit test và thử HTTP.

## Triển khai demo

Đã sao lưu DB trước cập nhật ở target/demo-before-plans.sql (không đưa dữ liệu/mật khẩu trong file này vào báo cáo). Flyway áp dụng V24 tới V27 thành công vào database demo; app, MySQL, Redis và MinIO vẫn chạy.

Portal: http://localhost:4301/plans
Kế hoạch mẫu đã kiểm tra: http://localhost:4301/plans/17/present
Giáo viên demo: demo.teacher@example.com / (mật khẩu: biến DEMO_TEACHER_PASSWORD trong .env.demo)
Admin demo: demo.admin@example.com / (mật khẩu: biến DEMO_ADMIN_PASSWORD trong .env.demo)

Một kế hoạch đầy đủ được giữ lại cho giáo viên demo; link trực tiếp nằm ở trường demoUrl trong scratch/plans-audit/results.json. Tài khoản đồng nghiệp và bộ/mẫu phụ chỉ dùng cho kiểm thử, được dọn sau mỗi lượt thành công. Video Google Developers và PDF synthetic chỉ để kiểm tra công nghệ, không phải nội dung dạy trẻ được tuyển chọn.

## Giới hạn

Chưa thử mọi loại MP4/audio thật trong màn trình chiếu mới; đã kiểm tra YouTube và PDF. Chưa mô phỏng mất mạng giữa buổi, mọi video bị giới hạn nhúng hoặc trình duyệt cũ. Không có chế độ offline, AI hay duyệt chuyên môn trong đợt này. Phát tệp gốc vẫn dùng cơ chế ghi bộ đếm/lịch sử tải hiện có. Xác nhận phát thử YouTube do giáo viên thực hiện.

