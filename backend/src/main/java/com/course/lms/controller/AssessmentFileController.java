package com.course.lms.controller;

import com.course.lms.dto.AssessmentFileDtos.*;
import com.course.lms.service.AssessmentFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Quiz et TP importés en fichier : sujets (enseignant), copies (étudiants), notes. */
@RestController
@RequestMapping("/api/assessment-files")
@PreAuthorize("isAuthenticated()")
public class AssessmentFileController {

    private final AssessmentFileService service;

    public AssessmentFileController(AssessmentFileService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public AssessmentFileView upload(@RequestParam Long chapterId, @RequestParam String type, @RequestParam String title,
                                     @RequestParam(required = false) String instructions, @RequestParam(required = false) String difficultyLevel,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String dueAt,
                                     @RequestParam(required = false) Integer durationMinutes, Authentication auth) {
        java.time.Instant due;
        try {
            due = dueAt == null || dueAt.isBlank() ? null : java.time.Instant.parse(dueAt);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Date limite invalide.");
        }
        return service.upload(auth.getName(), chapterId, type, title, instructions, difficultyLevel, file, due, durationMinutes);
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AssessmentFileView> teacherFiles(Authentication auth) {
        return service.teacherFiles(auth.getName());
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public List<SubmissionView> submissions(@PathVariable Long id, Authentication auth) {
        return service.submissions(auth.getName(), id);
    }

    @PutMapping("/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public SubmissionView grade(@PathVariable Long submissionId, @RequestBody GradeRequest request, Authentication auth) {
        return service.grade(auth.getName(), submissionId, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public SubmissionView submit(@PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication auth) {
        return service.submit(auth.getName(), id, file);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSubject(@PathVariable Long id, Authentication auth) {
        return send(service.downloadSubject(auth.getName(), id));
    }

    @GetMapping("/submissions/{submissionId}/download")
    public ResponseEntity<Resource> downloadSubmission(@PathVariable Long submissionId, Authentication auth) {
        return send(service.downloadSubmission(auth.getName(), submissionId));
    }

    private static ResponseEntity<Resource> send(Download d) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(d.path()));
    }
}
