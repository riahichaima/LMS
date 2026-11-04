package com.course.lms.repository;

import com.course.lms.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByTeacherIdOrderByStartAtAsc(Long teacherId);

    List<Meeting> findByCourseIdInOrderByStartAtAsc(Collection<Long> courseIds);

    List<Meeting> findAllByOrderByStartAtAsc();

    /** Réunions qui commencent dans moins d'une heure et dont le rappel n'est pas encore parti. */
    @Query("select m from Meeting m where m.reminderSent = false and m.startAt <= :limit and m.startAt > :notBefore")
    List<Meeting> findDueReminders(@Param("limit") Instant limit, @Param("notBefore") Instant notBefore);
}
