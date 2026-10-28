package com.example.heartlink.repository;

import com.example.heartlink.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);
    List<Message> findByMatchIdAndIdLessThanOrderByIdDesc(Long matchId, Long before, Pageable pageable);
}
