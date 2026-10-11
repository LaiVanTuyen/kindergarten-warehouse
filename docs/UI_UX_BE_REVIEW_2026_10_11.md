# Portal và backend — kiểm tra ngày 11/10/2026

## Thay đổi đã thực hiện

- Trang chủ: tìm nhanh, gợi ý từ khóa, trạng thái rỗng/lỗi riêng biệt và bố cục responsive.
- Thẻ tài liệu: sửa vùng bấm yêu thích bị `pointer-events-none`, tăng vùng bấm lên 44px và reset trạng thái ảnh lỗi khi đổi tài liệu.
- Chi tiết: không tải danh sách liên quan khi tài liệu chính không tồn tại hoặc không có chủ đề.
- Banner: giảm chiều cao và chuyển động, có nút tạm dừng, không autoplay khi tab ẩn hoặc bật reduced motion.
- Footer: bỏ thông tin liên hệ giả và link `#`, hướng người dùng tới quản trị kho học liệu của trường.
- Download: chỉ tăng lượt tải sau khi storage mở file thành công.
- Upload: đồng bộ ảnh PNG/JPG/JPEG/WebP và PPT/PPTX với enum/FE; kiểm tra định dạng trước khi upload và mở file mới trước khi xóa file cũ trong luồng thay file.
- CSRF: tái hiện cookie mất xen kẽ khi xác thực JWT mỗi request. Ngừng xoay token theo session; xoay token tại login thật. Test giữ bảo vệ CSRF cho request ghi.
- Storage local: build MinIO từ source chính thức ghim phiên bản vì image cũ không kéo được.
- Docker build: dùng Maven Java 17 hiện hành và cache dependency.

## Kiểm chứng

- Maven package: 146 test thường/security, không skip.
- MySQL 8.0.46 Testcontainers: 20 test visibility/favorites pass; Flyway áp dụng 23 migration.
- Portal Jest: 11/11 pass; build demo thành công.
- Kiểm tra trình duyệt tại 390px, 768px và 1365px: không tràn ngang.
- Tìm nhanh điều hướng đúng `/resources?search=...`.
- Smoke HTTP sau deploy local: 3 upload ảnh minh họa trả 201, list công khai trả 3 tài liệu; guest download trả 401, admin download trả 200 và dữ liệu từ storage.
- Cookie CSRF sau 3 GET đã đăng nhập giữ ổn định (1/1/1), thay vì mất xen kẽ như trước khi sửa.

Demo local: http://localhost:4201/ (FE), http://localhost:8080/api/v1/resources (BE). Ba tài liệu có nhãn `[DEMO]`, dùng ảnh minh họa đã có trong source; không chứa thông tin thật của trẻ.

## Chạy lại

```powershell
docker compose --env-file .env up -d --build
```

Test BE bằng JDK 17 trong container:

```powershell
docker run --rm -v "${PWD}:/workspace" -v warehouse_m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -B test
```

Testcontainers trên Docker Desktop hiện tại:

```powershell
docker run --rm -v "${PWD}:/workspace" -v warehouse_m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace maven:3.9-eclipse-temurin-17 mvn -B "-Dapi.version=1.44" -Pintegration-test failsafe:integration-test failsafe:verify
```

Các goal Failsafe ở trên yêu cầu test đã compile trước đó. Không vô hiệu hóa CSRF hoặc validation migration để demo.

Khi đã package và cần dùng dependency cache từ Maven container, có thể build runtime từ JAR:

```powershell
docker build -f docker/local-runtime.Dockerfile -t kindergarten-warehouse-app .
docker compose --env-file .env up -d --no-build app
```

## Giới hạn

Đây là đợt cải thiện Portal và lỗi BE đã xác minh, chưa phải nghiệm thu toàn bộ màn Admin/Teacher. Bundle vẫn vượt mức cảnh báo 500kB (chưa vượt mức lỗi 1MB); không dùng kết quả build để tuyên bố đạt tải production.

Source MinIO và license: https://github.com/minio/minio/tree/RELEASE.2025-10-15T17-29-55Z
