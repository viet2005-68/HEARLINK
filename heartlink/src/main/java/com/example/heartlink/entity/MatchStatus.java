package com.example.heartlink.entity;

/**
 * Trạng thái của một match.
 * <p>
 * active   - match đang hợp lệ, chưa bị huỷ
 * cancelled - match đã bị huỷ (soft delete), không xem trong GET /matches
 */
public enum MatchStatus {
    ACTIVE,
    CANCELLED
}
