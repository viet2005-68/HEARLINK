-- ===== Heartlink - Demo seed data =====
-- Chạy sau khi app đã khởi động (Hibernate tạo bảng):
--   make seed
-- hoặc:
--   docker compose exec -T postgres psql -U postgres -d heartlink < seed/seed.sql

-- Reset dữ liệu (CASCADE xóa luôn swipes + matches + refresh_tokens tham chiếu users)
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

-- ===== Users =====
-- Mật khẩu của tất cả user: 123456 (BCrypt)
INSERT INTO users (email, password_hash, name, bio, created_at) VALUES
    ('anna@example.com',   '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Anna',   'Thích đọc sách, đi dạo công viên', NOW()),
    ('binh@example.com',   '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Binh',   'Yêu bóng đá, mua sắm',             NOW()),
    ('carmen@example.com', '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Carmen', 'Làm đẹp, du lịch',                 NOW()),
    ('david@example.com',  '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'David',  'Game thủ, thích lập trình',        NOW()),
    ('eva@example.com',    '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Eva',    'Ẩm thực, yoga',                    NOW()),
    ('frank@example.com',  '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Frank',  'Thể thao, gia đình',               NOW()),
    ('grace@example.com',  '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Grace',  'Âm nhạc, học tiếng Anh',           NOW()),
    ('henry@example.com',  '$2a$10$DuSMEmXqBUBN2MaSDuCix.pqSO30r5FflH..jSf2yTzCgygxHwqva', 'Henry',  'Lập trình web, leo núi',           NOW());

-- ===== Swipes (like / pass) =====
INSERT INTO swipes (swiper_id, target_id, action, created_at) VALUES
    -- Anna (1)
    (1, 2, 'LIKE', NOW()),
    (1, 3, 'LIKE', NOW()),
    (1, 4, 'LIKE', NOW()),
    -- Binh (2)
    (2, 1, 'LIKE', NOW()),
    -- Carmen (3)
    (3, 1, 'LIKE', NOW()),
    (3, 4, 'PASS', NOW()),
    -- David (4)
    (4, 1, 'LIKE', NOW()),
    (4, 5, 'LIKE', NOW()),
    (4, 7, 'LIKE', NOW()),
    -- Eva (5)
    (5, 7, 'LIKE', NOW()),
    -- Frank (6)
    (6, 5, 'LIKE', NOW()),
    (6, 7, 'LIKE', NOW()),
    -- Grace (7)
    (7, 4, 'LIKE', NOW()),
    (7, 6, 'PASS', NOW()),
    -- Henry (8)
    (8, 1, 'PASS', NOW()),
    (8, 3, 'LIKE', NOW());

-- ===== Matches (cặp đã like nhau) =====
-- Ràng buộc: user_a_id < user_b_id, UNIQUE (user_a_id, user_b_id)
-- Derive matches from mutual likes so demo data obeys R3.
INSERT INTO matches (user_a_id, user_b_id, status, created_at)
SELECT s.swiper_id, s.target_id, 'ACTIVE', NOW()
FROM swipes s
JOIN swipes reverse_swipe
  ON reverse_swipe.swiper_id = s.target_id
 AND reverse_swipe.target_id = s.swiper_id
WHERE s.action = 'LIKE'
  AND reverse_swipe.action = 'LIKE'
  AND s.swiper_id < s.target_id;
