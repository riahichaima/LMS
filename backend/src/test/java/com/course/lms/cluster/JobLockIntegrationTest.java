package com.course.lms.cluster;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Deux backends partagent la même base : une tâche planifiée n'est lancée que par un seul à la fois. */
@SpringBootTest
class JobLockIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneBackendGetsTheJobUntilTheLockExpires() throws Exception {
        JobLock backend1 = new JobLock(jdbc);
        JobLock backend2 = new JobLock(jdbc);
        backend1.createTable();
        String job = "test-" + UUID.randomUUID().toString().substring(0, 8);

        assertThat(backend1.tryLock(job, Duration.ofMillis(800))).isTrue();
        assertThat(backend2.tryLock(job, Duration.ofMillis(800))).isFalse();
        assertThat(backend1.tryLock(job, Duration.ofMillis(800))).isFalse();

        Thread.sleep(1000);
        assertThat(backend2.tryLock(job, Duration.ofMillis(800))).isTrue();
        assertThat(jdbc.queryForObject("SELECT locked_by FROM scheduled_job_lock WHERE name = ?", String.class, job))
                .isEqualTo(backend2.instance());
    }
}
