# Baseline hiệu năng — trước khi sửa code

Đo ngày **2026-09-05** trên commit **`386610b`** (baseline nghiệp vụ, chưa sửa
dòng code nghiệp vụ nào). Đây là mốc để đối chiếu ở Tuần 7.

Script và dữ liệu thô: [`perf/`](../perf/). Kết quả lần đo này:
`perf/results/20260905-151029/`.

---

## 1. Điều kiện đo — phải giữ nguyên khi đo lại

| Hạng mục | Giá trị |
|---|---|
| Commit | `386610b` |
| Máy | Windows, 8 CPU logic |
| Docker | Desktop 29.4.3 — **quota 8 lõi, 9,54 GiB RAM** (`docker info`: NCPU=8) |
| Stack | `warehouse_demo` (compose demo), profile `prod,demo` |
| Dataset | 2.000 resources seed + 1 thật = **2.001**, trong đó **1.701** Portal thấy |
| Nguồn dataset | [`perf/seed-baseline.sql`](../perf/seed-baseline.sql) — sinh từ biến đếm, không dùng `RAND()`, chạy lại cho ra đúng cùng tập |
| k6 | image `grafana/k6:latest`, chạy **trong** `warehouse_demo_warehouse-network`, gọi thẳng nginx `portal` |
| Warm-up | 20s trước mỗi bài, **không** tính vào kết quả |
| Thời lượng đo | 60s |
| Chế độ download | `stream` — `StreamingResponseBody` qua Spring |
| Hikari pool | max 15 (mặc định `application.yml`) |

Gọi thẳng `portal` trong docker network để loại bỏ biến động của port-forward
trên host. Khi đo lại phải dùng đúng cách này.

Không trộn upload hay email vào bài đọc. Download đo riêng.

### 1.1 Cách đọc con số CPU

`docker stats` báo CPU **theo tổng số lõi logic**: 100 % = một lõi, nên trần
lý thuyết ở máy này là **800 %**.

Vì vậy:

- Giá trị **≥ 800 %** nghĩa là bão hoà hoàn toàn. Các số như 1040 % hay 1136 %
  ghi ở dưới là do `docker stats` lấy mẫu theo khoảng và có thể vọt lên trong
  chốc lát — **đừng đọc chúng như "10 lõi"**, chỉ nên đọc là "đã kịch trần".
- Khi đo lại ở Tuần 7 **bắt buộc giữ nguyên quota CPU và cùng máy**, và ghi lại
  `docker info` NCPU vào `context.txt`. So sánh CPU % giữa hai máy có số lõi
  khác nhau là vô nghĩa.

---

## 2. Kết quả ở mức tải mục tiêu

### 2.1 Đọc công khai — 350 VU (200 danh sách / 100 tìm kiếm / 50 chi tiết)

| Chỉ số | Đo được | Tiêu chí | Đạt? |
|---|---|---|---|
| Throughput | **6,3 req/s** | — | — |
| `t_list` P95 | **59,99 s** | < 500 ms | ✗ |
| `t_search` P95 | 48,21 s | < 500 ms | ✗ |
| `t_detail` P95 | 55,39 s | < 500 ms | ✗ |
| Tỷ lệ lỗi | **20,42 %** (116/568) | < 1 % | ✗ |
| MySQL CPU đỉnh | **1040 %** | — | bão hoà |
| App CPU đỉnh | 127 % | — | — |
| DB connection | **16** (pool 15 đã cạn) | không cạn | ✗ |

`t_list` P95 = 59,99s chính là **trần timeout 60s của k6**, không phải độ trễ
thật. Độ trễ thật lớn hơn con số này.

### 2.2 Đọc có đăng nhập — 150 VU (100 danh sách / 50 `/resources/me`)

| Chỉ số | Đo được | Tiêu chí | Đạt? |
|---|---|---|---|
| Throughput | 4,0 req/s | — | — |
| `t_auth_list` P95 | 35,92 s | < 500 ms | ✗ |
| `t_my_resources` P95 | 36,69 s | < 500 ms | ✗ |
| Tỷ lệ lỗi | 36,66 % | < 1 % | ✗ |
| MySQL CPU đỉnh | **1136 %** | — | bão hoà |
| App CPU đỉnh | 320 % | — | — |

### 2.3 Tải file — 50 VU đồng thời, file ~880 KB

| Chỉ số | Đo được | Ghi chú |
|---|---|---|
| Throughput | **28,4 req/s**, 26 MB/s | |
| `t_download` P50 | 754 ms | |
| `t_download` P95 | **1,59 s** | |
| `t_download` P99 | 2,17 s | |
| Tỷ lệ lỗi | 0,34 % | |
| App CPU đỉnh | **417 %** | ~4 lõi chỉ để đẩy byte |
| MySQL CPU đỉnh | 48,7 % | download hầu như không đụng DB |

**Download là phần duy nhất hoạt động chấp nhận được.** Nhưng 417 % CPU cho
việc truyền file là chi phí của `StreamingResponseBody` — mỗi lượt tải giữ một
thread servlet suốt thời gian truyền. Đây là con số Tuần 7 phải hạ bằng
X-Accel-Redirect, trong khi giữ nguyên hoặc tăng throughput.

---

## 3. Đường cong theo tải — phần quan trọng nhất để so sánh

Ở trạng thái bão hoà, P95 chỉ phản ánh trần timeout nên **không nhạy**. Ba
điểm dưới đây mới là mốc so sánh có ý nghĩa cho Tuần 7 (mỗi điểm: warm-up 15s,
đo 40s).

| VU | Throughput | `t_list` P95 | `t_search` P95 | `t_detail` P95 | Lỗi |
|---|---|---|---|---|---|
| **17** (10/5/2) | **106,7 req/s** | 5,35 s | **187 ms** | **111 ms** | 0 % |
| **35** (20/10/5) | 14,9 req/s | 5,23 s | 2,31 s | 2,33 s | 0 % |
| **87** (50/25/12) | 9,6 req/s | 11,69 s | 9,06 s | 8,98 s | 0 % |
| **350** (200/100/50) | 6,3 req/s | 59,99 s | 48,21 s | 55,39 s | 20,4 % |

Đọc bảng này theo cột, không theo hàng:

- Ở **17 VU**, `search` và `detail` hoàn toàn khoẻ (187 ms / 111 ms) trong khi
  `list` đã 5,35 s. Chỉ **một** endpoint hỏng.
- Từ **35 VU** trở lên, `search` và `detail` bị kéo tụt theo — từ 111 ms lên
  2,33 s rồi 8,98 s. Chúng không hề chậm đi vì bản thân chúng nặng hơn.
- Throughput **sụp từ 106,7 xuống 14,9 req/s** chỉ khi tăng từ 17 lên 35 VU.

Kết luận: `GET /resources` độc chiếm connection pool và **bỏ đói mọi endpoint
khác**. Sửa đúng một endpoint này sẽ kéo theo cả hệ thống.

---

## 4. Truy nguyên nguyên nhân

Chi phí khác nhau tuỳ mức tải, nên phải nêu cả hai.

### 4.1 Ở một request đơn lẻ: phần lớn thời gian nằm ngoài DB

> **Mức độ chắc chắn.** Con số ~690 ms ngoài DB là **đo được**. Việc quy nó cho
> `@EntityGraph` mới chỉ là **nghi phạm chính**, dựa trên tương quan: câu SQL
> mà Hibernate sinh ra thực sự có 4 lần join `users` với đầy đủ cột. Chưa chạy
> JFR hay profiler nên **chưa khẳng định được toàn bộ 690 ms là do hydrate 48
> entity `User`** — phần còn lại có thể nằm ở mapping DTO, serialize JSON, hoặc
> N+1 khi lấy `ageGroups`.
>
> Cách xác nhận: sau khi chuyển sang projection (việc #1 và #3 ở mục 5), đo lại
> cùng điều kiện. Nếu 690 ms giảm mạnh thì giả thuyết đúng; nếu không, chạy JFR
> trên `warehouse_demo_app` trước khi sửa tiếp.

Endpoint `GET /resources?page=0&size=12` mất **~800 ms**. Nhưng chạy thẳng SQL
tương đương trong MySQL chỉ mất:

| Truy vấn | Thời gian |
|---|---|
| Count cho phân trang | 39 ms |
| Lấy dữ liệu 12 dòng | 70 ms |
| **Tổng trong MySQL** | **~110 ms** |

**~690 ms còn lại nằm ngoài DB.**

Nguyên nhân ở [`ResourceRepository.java:12`](../src/main/java/com/kindergarten/warehouse/repository/ResourceRepository.java):

```java
@EntityGraph(attributePaths = { "topic", "topic.category",
        "topic.creator", "topic.updater", "creator", "updater" })
Page<Resource> findAll(Specification<Resource> spec, Pageable pageable);
```

Bốn đường dẫn trỏ tới `users` (`creator`, `updater`, `topic.creator`,
`topic.updater`). Hibernate sinh **4 lần `left join users`**, mỗi lần lấy
**toàn bộ cột**:

```sql
c1_0.password, c1_0.token_version, c1_0.original_email, c1_0.original_username,
c1_0.blocked_reason, c1_0.email, ...   -- lặp lại cho c5_0, u4_0, u6_0
```

Hai vấn đề:

1. **Hiệu năng** — mỗi trang 12 bản ghi phải hydrate tới 48 entity `User` đầy
   đủ, chỉ để hiển thị tên người đăng. Đây là công việc thừa đã xác nhận qua
   câu SQL; phần nó chiếm trong 690 ms thì chưa đo tách được.
2. **An toàn** — cột `password` và `token_version` được đọc vào bộ nhớ ứng dụng
   trên mỗi lần gọi một endpoint **công khai, không cần đăng nhập**. DTO không
   phơi ra ngoài nên chưa phải lỗ hổng, nhưng không có lý do gì để đọc chúng.

Ghi chú: các quan hệ này đã khai `FetchType.LAZY` trong `BaseEntity`.
`@EntityGraph` ghi đè điều đó. Sửa entity là vô ích nếu không sửa repository.

### 4.2 Dưới tải: MySQL bão hoà vì subquery tương quan

MySQL đạt **1040–1136 % CPU** trên máy 8 lõi — bão hoà hoàn toàn.

Thủ phạm là hai `@Formula`, chạy như subquery tương quan **trên từng dòng trả
về**:

| Vị trí | Công thức |
|---|---|
| [`Topic.java:45`](../src/main/java/com/kindergarten/warehouse/entity/Topic.java) | `(SELECT COUNT(*) FROM resources r WHERE r.topic_id = id AND r.is_deleted = false)` |
| [`Category.java:49`](../src/main/java/com/kindergarten/warehouse/entity/Category.java) | `(SELECT COUNT(*) FROM topics t WHERE t.category_id = id AND t.is_deleted = false)` |

Vì `topic` và `topic.category` bị fetch-join cho **mọi** resource, hai công
thức này được tính lại cho từng dòng. Với 16 topic × ~125 resource mỗi topic,
mỗi trang phải đếm lại hàng nghìn dòng — và nhân lên theo số request đồng thời.

Đã kiểm tra và **loại trừ** phân trang trong bộ nhớ: log không có cảnh báo
`HHH000104`, vì `@EntityGraph` chỉ chứa quan hệ `@ManyToOne`, không có
collection.

### 4.3 Pool 15 connection biến chậm thành sụp

`spring.datasource.hikari.maximum-pool-size: 15`. Khi mỗi truy vấn `list` giữ
connection hàng giây, 15 connection bị chiếm hết và **mọi** request khác xếp
hàng — kể cả `detail` vốn chỉ tốn 111 ms.

Đó là lý do throughput sụp từ 106,7 xuống 14,9 req/s chỉ sau khi tăng gấp đôi
số VU.

**Đừng vội nâng pool size.** Pool lớn hơn sẽ đẩy thêm tải vào MySQL vốn đã
bão hoà. Phải sửa truy vấn trước, đo lại, rồi mới xét pool.

---

## 5. Việc cần làm, xếp theo tỉ lệ lợi ích trên công sức

| # | Việc | Kỳ vọng |
|---|---|---|
| 1 | Bỏ `topic.creator`, `topic.updater`, `creator`, `updater` khỏi `@EntityGraph`; lấy tên người đăng bằng projection | Bỏ 4 join `users`, hết đọc `password` |
| 2 | Bỏ hai `@Formula`; tính `resourceCount`/`topicCount` bằng truy vấn gộp riêng, có cache | Bỏ subquery tương quan trên từng dòng |
| 3 | Dùng DTO projection cho danh sách thay vì hydrate entity đầy đủ | Nhắm vào phần lớn trong ~690 ms ngoài DB — **đo lại để xác nhận**, xem §4.1 |
| 4 | Thêm index theo filter thật sau khi đã sửa 1–3 | Chỉ có nghĩa sau khi hết quét thừa |
| 5 | Xét lại pool size — **sau** khi đo lại | Tránh đẩy thêm tải vào DB đang bão hoà |
| 6 | Chuyển download sang X-Accel-Redirect | Hạ 417 % CPU app khi tải file |

Mục 1 và 2 nên làm cùng nhau và đo lại ngay: cả hai đều nằm trên đường đi của
`GET /resources`.

---

## 6. Tiêu chí để coi là đạt ở Tuần 7

Đo lại **đúng điều kiện mục 1**, so với bảng mục 3:

- [ ] Ở 17 VU: `t_list` P95 < 500 ms (baseline 5,35 s)
- [ ] Ở 35 VU: `t_search` và `t_detail` P95 giữ được < 500 ms (baseline 2,3 s)
- [ ] Ở 87 VU: throughput > 100 req/s (baseline 9,6 req/s)
- [ ] Ở 350 VU: tỷ lệ lỗi < 1 % (baseline 20,4 %)
- [ ] MySQL CPU không còn bão hoà ở mức tải 87 VU
- [ ] Download: giữ throughput ≥ 28 req/s với App CPU **giảm rõ rệt** so với 417 %
- [ ] Không lần đo nào chạm trần timeout 60s

---

## 7. Cách chạy lại

```bash
# 1) Stack demo phải đang chạy
docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml up -d

# 2) Nạp lại đúng dataset (xoá và tạo lại phần perf-seed-*)
docker exec -i warehouse_demo_mysql \
  mysql -uroot -p<MYSQL_ROOT_PASSWORD> warehouse_demo_db < perf/seed-baseline.sql

# 3) Chạy đủ ba bài (warm-up + đo)
bash perf/run-baseline.sh

# 4) Ba điểm đường cong tải
for s in 0.05 0.10 0.25; do
  docker run --rm --network warehouse_demo_warehouse-network \
    -v "$PWD/perf/k6:/scripts:ro" \
    -e BASE_URL=http://portal -e DURATION=40s -e VU_SCALE=$s \
    grafana/k6:latest run /scripts/public-read.js
done
```

Lưu ý khi chạy lại bài download: `perf/k6/download-ids.json` chứa id của lần
seed trước. Sau khi seed lại phải xuất lại danh sách id, nếu không bài download
sẽ toàn 404.

Luôn ghi commit hash vào `context.txt` (script tự làm) để hai lần đo đối chiếu
được với nhau.
