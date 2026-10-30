# Heartlink — Backend ứng dụng hẹn hò, Phase 1

## 1. Bài toán và phạm vi

Heartlink giúp người dùng tìm người muốn làm quen, ghép đôi khi cả hai cùng thích nhau và nhắn tin sau khi ghép đôi. Ví dụ: Anna thích Bình thì hệ thống chỉ lưu lượt thích. Khi Bình cũng thích Anna, hệ thống tạo một **match** (cặp ghép đôi), thông báo cho hai người và cho phép họ trò chuyện.

Người dùng có thể tạo tài khoản, đăng nhập, chỉnh sửa hồ sơ và xem hồ sơ của người khác để chọn thích hoặc bỏ qua. Khi hai người cùng thích nhau, hệ thống sẽ ghép đôi, gửi thông báo và cho phép họ nhắn tin. Người dùng cũng có thể xem danh sách những người đã ghép đôi với mình hoặc hủy ghép đôi.

**Tech stack**

| Công nghệ | Mục đích |
|---|---|
| Java 21, Spring Boot 4.1.1 | Xây dựng backend và API |
| Spring Data JPA, Hibernate | Đọc/ghi database |
| PostgreSQL 16 | Lưu dữ liệu |
| JWT, BCrypt | Xác thực và băm mật khẩu |
| SSE | Gửi thông báo trực tiếp |
| OpenAPI, Swagger UI | Mô tả và gọi thử API |
| Maven, Docker Compose | Build và chạy dự án |

## 2. Kiến trúc dự án và tổ chức mã nguồn

Dự án dùng kiến trúc **monolithic**: các chức năng đăng nhập, quẹt, match, chat và thông báo cùng chạy trong **một ứng dụng Spring Boot**, dùng chung **một database PostgreSQL**.

Mã nguồn chia theo chức năng: `auth`, `user`, `recommendation`, `swipe`, `match`, `chat`, `notification`. Mỗi module giữ controller, service và DTO của mình; entity và repository nằm trong các package dùng chung.


Luồng xử lý request bên trong ứng dụng:

```text
Client → JWT filter → Controller → Service → Repository → PostgreSQL
```

Controller nhận request; service kiểm tra quy tắc và xử lý nghiệp vụ; repository đọc/ghi database. Entity mô tả dữ liệu lưu trong bảng, còn DTO chọn các trường được nhận/trả qua API, tránh đưa mật khẩu băm ra ngoài.

```text
heartlink/
├── pom.xml, mvnw, mvnw.cmd        # Thư viện và công cụ build Maven
├── Dockerfile, docker-compose.yml # Đóng gói và chạy app + database
├── openapi.yaml, seed/seed.sql    # Đặc tả API và dữ liệu demo
└── src/
    ├── main/java/com/example/heartlink/
    │   ├── HeartlinkApplication.java # Điểm khởi động
    │   ├── auth/                    # Đăng ký, đăng nhập, refresh, logout
    │   ├── user/                    # Xem và sửa hồ sơ của mình
    │   ├── recommendation/          # Lấy hồ sơ chưa quẹt
    │   ├── swipe/                   # Like/pass, kiểm tra like hai chiều
    │   ├── match/                   # Tạo, xem và hủy cặp ghép đôi
    │   ├── chat/                    # Gửi và đọc tin nhắn theo match
    │   ├── notification/            # Lịch sử thông báo và kết nối SSE
    │   ├── entity/, repository/     # Dữ liệu và truy vấn
    │   └── common/                  # JWT và xử lý lỗi chung
    ├── main/resources/              # Cấu hình thường và Docker
    └── test/java/                   # Kiểm thử
```

Database có 6 bảng: `users` (tài khoản), `swipes` (like/pass), `matches` (cặp ghép đôi), `messages` (tin nhắn), `notifications` (thông báo), `refresh_tokens` (token đã hash).

Hai yêu cầu kỹ thuật cần giữ khi sửa code:

- **Không lưu dở dang luồng match:** swipe mới, match và hai thông báo cùng nằm trong transaction; lỗi làm rollback thì hoàn tác các thay đổi đó. Trước khi kiểm tra swipe, service khóa hai dòng user theo ID tăng dần để phối hợp các request đồng thời. Sự kiện SSE chỉ được gửi sau khi commit thành công.
- **Không gửi tin sau khi hủy match:** thao tác gửi giữ khóa dòng match đến cuối transaction. Thao tác hủy cập nhật cùng dòng nên phải chờ nhau; nếu hủy hoàn tất trước thì gửi sau bị từ chối.

## 3. Luồng sử dụng và quy tắc nghiệp vụ

1. **Đăng ký, đăng nhập:** người dùng đăng nhập bằng email và mật khẩu. Backend trả token để xác định tài khoản trong các lần gọi API tiếp theo. Mật khẩu được hash bằng BCrypt trước khi lưu.
2. **Xem hồ sơ:** người dùng có thể xem/sửa tên và giới thiệu của mình. Danh sách gợi ý loại chính mình và những người mình đã quẹt; hiện chỉ sắp xếp theo ID, chưa chọn theo tuổi, vị trí hay sở thích.
3. **Quẹt hồ sơ:** `like` là thích, `pass` là bỏ qua. Không được tự quẹt hoặc quẹt cùng một người nhiều lần. Người đã like mình vẫn xuất hiện nếu mình chưa quẹt họ.
4. **Tạo match:** khi có like hai chiều, backend tạo match ngay trong lần quẹt thứ hai và ghi một thông báo cho mỗi người. Trong database, ID nhỏ luôn nằm ở `user_a_id`, ID lớn ở `user_b_id` để lưu cặp theo cùng một thứ tự.
5. **Chat:** chỉ hai người trong match đang hoạt động được gửi và đọc tin. Nội dung không được chỉ có khoảng trắng, tối đa 2.000 ký tự Java. Client gọi lại API định kỳ để lấy tin mới (polling); hiện chưa có chat realtime, ảnh hay trạng thái đã xem.
6. **Hủy match:** một trong hai người có thể hủy. Backend đổi trạng thái sang `CANCELLED`, giữ dữ liệu cũ nhưng chặn các yêu cầu gửi/đọc tin mới sau khi hủy. Swipe cũ vẫn còn nên không thể quẹt lại để tạo match mới.
7. **Thông báo:** người dùng xem lịch sử hoặc mở kết nối SSE để server gửi thông báo match mới xuống khi đang kết nối. SSE giữ kết nối HTTP để gửi nhiều sự kiện. Thông báo đã lưu vẫn đọc được khi người dùng quay lại app, kể cả sau khi hủy match.

## 4. API và cách xem đặc tả

Đặc tả nằm trong **[heartlink/openapi.yaml](heartlink/openapi.yaml)**. Có thể mở file bằng công cụ hỗ trợ preview OpenAPI, chẳng hạn Swagger Editor. Khi ứng dụng đang chạy, mở [Swagger UI](http://localhost:8080/swagger-ui/index.html) để xem và gọi thử API; bản đặc tả do ứng dụng sinh ra nằm tại `/v3/api-docs`.

Các API nghiệp vụ cần header `Authorization: Bearer <access_token>`, ngoại trừ nhóm `/api/v1/auth/**`. Backend lấy danh tính từ token; client không tự chọn người gửi tin hoặc người thực hiện lượt quẹt.

| API | Công việc |
|---|---|
| `POST /api/v1/auth/register` | Tạo tài khoản |
| `POST /api/v1/auth/login` | Đăng nhập, nhận access token và refresh token |
| `POST /api/v1/auth/refresh` | Đổi refresh token còn hiệu lực lấy bộ token mới |
| `POST /api/v1/auth/logout` | Thu hồi refresh token được gửi lên |
| `GET /api/v1/users/me` | Xem hồ sơ của mình |
| `PATCH /api/v1/users/me` | Sửa tên, giới thiệu; `bio: null` để xóa giới thiệu |
| `GET /api/v1/recommendations?limit=20` | Lấy hồ sơ chưa quẹt |
| `POST /api/v1/swipes` | Gửi `{ "target_id": 2, "action": "like" }` hoặc `pass` |
| `GET /api/v1/matches?limit=20&offset=0` | Xem các match đang hoạt động |
| `DELETE /api/v1/matches/{id}` | Hủy match |
| `POST /api/v1/matches/{matchId}/messages` | Gửi `{ "content": "Chào bạn!" }` |
| `GET /api/v1/matches/{matchId}/messages?limit=20` | Đọc tin; gửi tiếp `before=next_before` để lấy tin cũ |
| `GET /api/v1/notifications?limit=20` | Đọc thông báo; gửi tiếp `cursor=next_cursor` để lấy trang cũ |
| `GET /api/v1/notifications/stream` | Mở SSE nhận sự kiện `match.created` |

API dùng mã HTTP thông thường: `200/201` khi thành công, `204` khi hủy/logout thành công, `400` khi đầu vào không hợp lệ, `401` khi xác thực không hợp lệ, `403` khi thiếu quyền, `404` khi không tìm thấy tài nguyên theo quy tắc API và `409` khi email/lượt quẹt bị trùng. Lỗi nghiệp vụ có dạng:

```json
{ "error": { "code": "ALREADY_SWIPED", "message": "You have already swiped this user" } }
```

Access token mặc định sống 1 giờ, refresh token sống 7 ngày. Database chỉ lưu hash của refresh token; mỗi lần refresh sẽ thu hồi token cũ. Logout thu hồi refresh token được gửi, còn access token đã phát vẫn dùng được tới lúc hết hạn.

## 5. Cách build, chạy và thử dự án

Cần **JDK 21, Docker và Docker Compose v2**; lần đầu cần mạng để tải Maven, thư viện và image. Từ thư mục gốc repo:

```bash
cd heartlink
./mvnw clean package -DskipTests
docker compose up -d --build
```

Maven tạo JAR trong `target/`. Dockerfile chép JAR đã build vào image có Java runtime, nên phải build JAR trước. Compose chạy hai container, chờ PostgreSQL sẵn sàng rồi khởi động app, lưu dữ liệu DB trong volume `postgres_data`.

Mở Swagger tại **http://localhost:8080/swagger-ui/index.html**. Sau khi app đã khởi động và tạo bảng, có thể nạp dữ liệu demo:

```bash
docker compose exec -T postgres psql -U postgres -d heartlink < seed/seed.sql
```

**Seed xóa dữ liệu hiện có rồi nạp lại** 8 tài khoản, các lượt quẹt, 4 match và 8 thông báo. Đăng nhập thử bằng `anna@example.com` / `123456`, sao chép `access_token` vào nút **Authorize** trên Swagger. Muốn demo like hai chiều từ đầu, đăng ký hai tài khoản mới rồi lần lượt like nhau, lấy ID match để chat và thử hủy.

Khi phát triển có thể chỉ chạy DB trong Docker rồi chạy app trong IDE hoặc bằng Maven:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

App chạy trực tiếp kết nối `localhost:5432`; app trong Docker dùng profile `docker`, kết nối tới service `postgres:5432`. Database mặc định là `heartlink`, tài khoản `postgres` / `123456`. Hibernate tạo/cập nhật bảng khi app khởi động (`ddl-auto=update`). Trước khi triển khai thực tế cần thay thông tin mặc định, cấp `JWT_SECRET` riêng (ít nhất 32 byte) cho app và quản lý thay đổi schema bằng migration.

Lệnh thường dùng: `docker compose logs -f app` để xem log; `docker compose down` để dừng nhưng giữ dữ liệu. Thêm `-v` sẽ xóa cả volume dữ liệu.

## 6. Vấn đề dễ thấy và hướng Phase 2

**Ở đây, nhóm chú trọng một bài toán cụ thể: Tập trung vào việc khi lượng dữ liệu lớn, các hoạt động đọc ghi vào cơ sở dữ liệu diễn ra nhiều thì có gây ra hiện tượng chậm phản hồi hay không? Vì vậy nhóm đang chọn những công nghệ cụ thể gần gũi nhất, không làm bài toán bị phân rã thành các miền khác nhau, mà tập trung vào một khía cạnh duy nhất: Các thao tác vào cơ sở dữ liệu.**

**Khi A swipe B mà không khóa lại 2 dòng dữ liệu thì có thể xảy ra tình trạng khi A và B cùng swipe nhau trong 1 thời điểm, khi kiểm tra đối phương chưa thích lại => sai lệch dữ liệu, sẽ không bao giờ vào được bảng matches. Nhóm đang chọn solution dễ nhất và đơn giản nhất là khóa theo dòng của các user, chính vì vậy nên cũng sẽ gây ra hiện tượng phải chờ nhả khóa mới có thể thực hiện được tiếp**

**Các vấn đề đi sau là những vấn đề dễ thấy hơn, đọc để loại người đã thích, quá tải lượt swipe, nhiều tài nguyên cho chat, mất response hoặc là rate-limiting lượt quẹt**


| Hiện trạng Phase 1 | Hướng cải tiến cần xem xét |
|---|---|
| **1. Hồ sơ nổi tiếng có thể làm chậm trải nghiệm ghép đôi.** Ví dụ A được rất nhiều người cùng bấm thích. Mỗi lượt quẹt phải khóa cả dòng người gửi và người nhận, nên các request cùng liên quan A phải chờ nhau. Nếu A cũng bấm thích lại B trong lúc đó, request của A cũng có thể phải chờ: hai người đã bấm thích nhưng chưa thấy match và thông báo ngay, làm trải nghiệm thiếu tức thời. SSE chỉ gửi thông báo sau khi match đã được tạo và lưu thành công. | Cân nhắc khóa theo cặp để A–B và A–C xử lý song song, còn A–B và B–A vẫn dùng chung khóa. Cần kiểm tra tính đúng khi like đồng thời và đo riêng thời gian chờ khóa trước khi quyết định cải tiến. |
| **2. Quẹt càng nhiều, lấy gợi ý càng chậm.** Ví dụ B đã quẹt 20.000 hồ sơ đầu tiên. Khi B mở app để xem tiếp, truy vấn hiện tại vẫn xét từ đầu danh sách, loại các hồ sơ đã quẹt rồi mới lấy đủ 20 hồ sơ chưa xem. Lịch sử càng dài thì phần phải bỏ qua càng lớn. | Tối ưu cách lấy hồ sơ chưa xem để giảm việc duyệt lại dữ liệu đã quẹt: kiểm tra index/truy vấn, dùng cursor để tiếp tục từ vị trí đã xem, và cân nhắc bộ nhớ đệm nếu cần. So sánh thời gian truy vấn theo độ dài lịch sử và bảo đảm không gợi ý lại hồ sơ đã quẹt. |
| **3. Swipe quá tải có thể làm chậm chức năng khác.** Các chức năng đang dùng chung pool kết nối database. Ví dụ nhiều lượt swipe chờ khóa chiếm hết các kết nối, một người khác chỉ muốn đọc tin nhắn cũng phải chờ có kết nối trống, dù không liên quan đến hồ sơ đang được quẹt. Đây là rủi ro cần đo thêm khi chạy nhiều chức năng cùng lúc. | Phân bổ và giới hạn tài nguyên giữa các nhóm chức năng để swipe không chiếm hết phần phục vụ chat hoặc đọc hồ sơ, giảm ảnh hưởng lan sang người dùng khác. |
| **4. Chat tạo tải thừa khi không có tin mới.** Hiện backend chưa đẩy tin chat mới xuống client. Nếu client gọi lại API định kỳ để cập nhật cuộc trò chuyện, mỗi lần vẫn truy vấn và trả các tin gần nhất dù không ai nhắn thêm. Càng nhiều người mở chat thì càng nhiều request chỉ tải lại dữ liệu cũ. | Cải thiện cách cập nhật tin nhắn để giảm các lần gọi API và đọc database không cần thiết, đồng thời giúp người nhận thấy tin mới sớm hơn. Có thể đẩy tin mới qua kênh SSE hiện có (kết hợp pub/sub nếu chạy nhiều instance), hoặc giữ polling nhưng dùng ETag/304 Not Modified. |
| **5. Mất phản hồi khiến người dùng không biết lượt thích đã thành công chưa.** Ví dụ B thích A, server đã lưu và tạo match nhưng phản hồi bị mất do mạng. Khi B gửi lại, Phase 1 trả `409 ALREADY_SWIPED` thay vì kết quả trước đó. Dữ liệu không bị tạo trùng nhưng client chưa nhận lại được kết quả thành công. | Cải thiện việc xử lý yêu cầu gửi lại khi mạng chập chờn, để người dùng nhận được kết quả lượt thích đã thực hiện mà không tạo thêm swipe hoặc match trùng, hoặc có thể sử dụng các idempotent endpoint/ idempotent key. |
| **6. Chưa giới hạn tần suất để hạn chế spam/bot.** Ví dụ một tài khoản dùng bot để quẹt hàng nghìn hồ sơ hoặc gửi tin liên tục, làm tăng tải và ảnh hưởng người dùng khác. Phase 1 có JWT để xác định tài khoản nhưng chưa giới hạn số lần gọi API; bot có token hợp lệ vẫn gửi được request liên tục. | Bổ sung cơ chế hạn chế các thao tác quá dồn dập để giảm spam, bảo vệ tài nguyên hệ thống và tránh ảnh hưởng người dùng khác. Có thể giới hạn theo từng thao tác và từng user (ví dụ token bucket), trả 429 kèm Retry-After, đặt hạn mức swipe, và bổ sung phát hiện bot theo tín hiệu hành vi. |

## 7. Tài liệu và notebook benchmark

- [Đặc tả API (OpenAPI)](heartlink/openapi.yaml).
- Thiết kế database: [v1.1](heartlink/docs/Database-v1.1.pdf), [v1.0](heartlink/docs/Database-v1.0.pdf).
- [Tài liệu đánh giá Phase 1 v1.0](heartlink/docs/Evaluation-Phase1-v1.0.pdf).
- [Notebook benchmark trên Kaggle](heartlink/benchmark/heartlink_kaggle_bench.ipynb).
