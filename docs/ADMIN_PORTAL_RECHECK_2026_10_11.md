# Kiểm tra lại Admin và Portal — 11/10/2026

Báo cáo này cập nhật kết quả của DEMO_REVIEW_2026_10_11.md sau phản ánh lỗi banner, YouTube và Dashboard. Môi trường thử là warehouse_demo riêng, dữ liệu thao tác là tài khoản và học liệu mẫu.

## Những lỗi đã sửa

- Banner: FE gọi sai PATCH thay vì PUT; chặn nhấn lặp, hoàn trả đúng dòng khi lỗi. Backend lưu visibility khi tạo/sửa, Portal loại banner PRIVATE kể cả khi admin đang đăng nhập. Thay ảnh mới thành công rồi mới dọn ảnh cũ sau commit.
- Dashboard: bỏ toàn bộ mock, thêm GET /api/v1/admin/dashboard lấy số liệu DB và bộ đếm Redis. Duyệt/từ chối dùng UUID thật và tải lại snapshot. Tổng hiện tại/tích lũy được phân biệt với thống kê theo khoảng ngày; bỏ biểu đồ lịch sử lượt xem vì chưa có dữ liệu. Lỗi tải dữ liệu không giữ số cũ gây hiểu nhầm.
- YouTube: đọc đúng watch, youtu.be, Shorts, live và embed; kiểm tra host và ID. Portal và Admin có preview; nguồn YouTube có form nhập riêng; iframe dùng origin/referrer đúng. Portal có liên kết mở YouTube khi video không cho nhúng. Không đưa URL ngoài tùy ý vào iframe.
- Tài nguyên: sửa method/body xóa và khôi phục hàng loạt, payload duyệt/từ chối resourceIds, trạng thái ARCHIVED. Admin Word/PDF/Office dùng cùng bộ đọc nội bộ với Portal. Portal có player âm thanh/video tệp cho người đăng nhập.
- Người dùng: bộ lọc roles/statuses đúng backend; tài khoản xóa mềm được nhận diện để khôi phục. Đổi mật khẩu kết thúc phiên hiện tại; đăng nhập mới không bị cookie đã thu hồi chặn. Đặt lại mật khẩu ở cả hai app dùng email/OTP theo API thực tế.
- Phân loại: emoji được chuyển thành tệp PNG đúng hợp đồng multipart; sửa phân loại giữ ảnh cũ. Bỏ trường platform không được backend lưu.
- Thông báo: tránh hai toast cho một lỗi; nhiều yêu cầu 401 cùng lúc chỉ thông báo hết phiên một lần. Giới hạn đếm view 429 được xử lý như lần xem lặp dự kiến. Thời gian nhật ký UTC hiển thị đúng múi giờ.
- Nhật ký: sửa hoàn tất tải CSV bất đồng bộ, giữ kiểm tra ADMIN ở yêu cầu ban đầu.

## Bằng chứng

- Hai app đã build cấu hình demo thành công.
- Backend package và test security: 169/169 đạt, không skip (target/admin-portal-audit-final-build.log).
- MySQL thật: 22/22 integration tests đạt, không skip (target/admin-portal-audit-integration.log).
- FE: Portal 19/19, data-access 14/14, thời gian Admin 2/2.
- Chrome thao tác thật: 19 bước, 65 API calls đạt cho banner, YouTube upload, PDF upload, Dashboard đối chiếu dữ liệu, duyệt đơn/hàng loạt, tài nguyên xóa/khôi phục và đọc các màn Admin. Bằng chứng: repo Angular scratch/admin-portal-audit/results.json.
- Video mẫu đã phát hơn 2 giây, paused=false, readyState=4: scratch/admin-portal-audit/youtube-playback.json và youtube-playing.png.
- Đợt CRUD bổ sung: 11 bước, 52 API calls đạt; khóa/mở khóa, sửa/xóa/khôi phục người dùng qua UI; tạo/sửa phân loại, CRUD chủ đề, hồ sơ, xuất CSV, đổi mật khẩu và đăng nhập lại, kiểm tra hai form email/OTP. Bằng chứng: scratch/admin-portal-audit/crud-results.json; ghi rõ bước nào qua UI, bước nào qua API.
- Lượt kiểm tra lại Portal/Admin cơ bản: 20 bước/màn, 56 API calls đạt; gồm bình luận, bật/tắt yêu thích, upload và duyệt qua giao diện (scratch/demo-review/business-results.json).
- Trước đó PDF/DOCX/PPTX/XLSX đã chuyển đổi READY lần lượt 3/3/2/1 trang; quyền guest/tệp gốc/riêng tư được kiểm tra.

## Phạm vi còn chưa xác minh

SMTP demo chưa cấu hình nên chưa xác minh email OTP được gửi/nhận ngoài hệ thống. Luồng player MP4/audio mới có giao diện, chưa có bằng chứng phát tệp thật trong đợt này. Chưa kiểm thử mọi tổ hợp dữ liệu, video YouTube bị chủ sở hữu chặn nhúng, hoặc tải lớn/đồng thời. Các kết quả trên không có nghĩa toàn bộ dự án không còn lỗi.

## Mở bản demo

Admin: http://localhost:4300/dashboard
Portal: http://localhost:4301
Admin demo: demo.admin@example.com / (mật khẩu: biến DEMO_ADMIN_PASSWORD trong .env.demo)
Giáo viên demo: demo.teacher@example.com / (mật khẩu: biến DEMO_TEACHER_PASSWORD trong .env.demo)

Tải lại tab bằng Ctrl+F5 để nhận bundle FE mới. Link video mẫu được ghi trong scratch/admin-portal-audit/results.json, trường youtubeUrl.


