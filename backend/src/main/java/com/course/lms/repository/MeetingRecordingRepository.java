package com.course.lms.repository;

import com.course.lms.model.MeetingRecording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, Long> {

    List<MeetingRecording> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    List<MeetingRecording> findByMeetingIdInOrderByCreatedAtDesc(Collection<Long> meetingIds);

    long countByMeetingId(Long meetingId);
}
