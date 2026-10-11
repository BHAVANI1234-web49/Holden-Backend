package com.example.holden.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.holden.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUsernameOrderByTimestampDesc(String username);

    List<ActivityLog> findTop20ByUsernameOrderByTimestampDesc(String username);
}
