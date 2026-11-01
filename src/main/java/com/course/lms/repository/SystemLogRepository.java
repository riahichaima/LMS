package com.course.lms.repository;

import com.course.lms.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {

    long countByLevelAndCreatedAtAfter(String level, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    @Query("select l.createdAt from SystemLog l where l.action = :action and l.createdAt >= :after")
    List<LocalDateTime> findDatesByActionSince(@Param("action") String action, @Param("after") LocalDateTime after);

    @Modifying
    @Query("delete from SystemLog l where l.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
