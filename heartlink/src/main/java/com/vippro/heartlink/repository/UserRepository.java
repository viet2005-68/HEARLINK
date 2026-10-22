package com.vippro.heartlink.repository;

import com.vippro.heartlink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
