package com.example.heartlink.repository;

import com.example.heartlink.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable page);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long beforeId, Pageable page);
}
