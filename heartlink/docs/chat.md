# Chat text theo match

Một match đang hoạt động là một cuộc trò chuyện giữa hai người. Tin nhắn lưu trong bảng `messages`, với FK `match_id` đến `matches` và `sender_id` đến `users`. Hibernate tạo bảng theo cấu hình development hiện tại.

## Gửi tin

```http
POST /api/v1/matches/42/messages
Authorization: Bearer <access_token>
Content-Type: application/json

{ "content": "Chào bạn!" }
```

Response `201`:

```json
{
  "id": 124,
  "sender_id": 1,
  "content": "Chào bạn!",
  "created_at": "2026-10-28T09:00:00Z"
}
```

Nội dung không được rỗng hoặc chỉ chứa khoảng trắng, tối đa 2.000 ký tự Java. Backend lấy người gửi từ JWT. Gửi tin giữ khoá bản ghi match đến lúc transaction kết thúc; unmatch dùng cùng bản ghi nên phải chờ thao tác gửi hoàn tất hoặc ngược lại. Nếu unmatch hoàn tất trước, request gửi sau sẽ bị từ chối.

## Đọc lịch sử

```http
GET /api/v1/matches/42/messages?limit=20
Authorization: Bearer <access_token>
```

```json
{
  "items": [
    { "id": 124, "sender_id": 1, "content": "Chào bạn!", "created_at": "2026-10-28T09:00:00Z" }
  ],
  "next_before": null
}
```

Tin mới nằm trước, sắp xếp theo ID giảm dần. Để lấy tin cũ, truyền `before` bằng `next_before` của trang trước. `next_before: null` là hết lịch sử. `limit` mặc định 20, cho phép 1–50. Query luôn lọc theo match nên ID cursor của match khác không làm lộ tin nhắn.

Cả hai API yêu cầu JWT và người dùng tồn tại. Người ngoài match nhận `403 MATCH_FORBIDDEN`; thành viên của match đã huỷ hoặc match không tồn tại nhận `404 MATCH_NOT_FOUND`. Huỷ match giữ lịch sử trong DB nhưng chặn gửi/đọc mới. Request đọc đã được chấp nhận trước khi unmatch có thể vẫn hoàn tất.

Bản đầu dùng polling: khi mở chat gọi GET không có `before`, gọi lại định kỳ nếu cần và gộp kết quả theo ID. Chưa có realtime, đọc/đã xem, sửa/xoá tin hoặc gửi ảnh. Gửi lại POST tạo một tin nhắn mới; chưa có idempotency key. Client hiển thị nội dung dưới dạng text, không render thành HTML.

## Kiểm tra

Chạy trong `heartlink/`, dùng riêng PostgreSQL test (cổng 55432):

```bash
docker compose -f docker-compose.test.yml up -d --wait
HEARTLINK_DB_TESTS=true ./mvnw -Dtest=Phase1ApiTests test
docker compose -f docker-compose.test.yml stop
```

Các ca chat kiểm tra JWT, nội dung và phân trang, lưu tin hai chiều, chặn người ngoài/cursor chéo match, và giữ dữ liệu nhưng chặn truy cập sau unmatch.
