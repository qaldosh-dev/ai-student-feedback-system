package kz.sdu.feedback.schedule.service;

import java.util.*;
import kz.sdu.feedback.schedule.dto.ReviewEligibilityResponse;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.repository.VerifiedCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only authorization evidence. Never invokes matching or creates verification records. */
@Service
@Transactional(readOnly = true)
public class ReviewEligibilityService {
    private final ScheduleUploadService schedules;
    private final VerifiedCourseRepository verified;

    public ReviewEligibilityService(ScheduleUploadService schedules, VerifiedCourseRepository verified) {
        this.schedules = schedules;
        this.verified = verified;
    }

    public List<ReviewEligibilityResponse> list(String authenticatedEmail) {
        var owner = schedules.requireStudent(authenticatedEmail);
        var groups = new HashMap<Target, List<VerifiedCourse>>();
        for (var row : verified.findEligibilityEvidence(owner.getId(), ScheduleUpload.Status.VALID)) {
            if (!consistentEvidence(row)) continue;
            var key = new Target(row.getCourseOffering().getId(), row.getSourceEntry().getAcademicTerm());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        var result = new ArrayList<ReviewEligibilityResponse>();
        groups.forEach((key, rows) -> {
            var offering = rows.getFirst().getCourseOffering();
            var sections = rows.stream().map(v -> v.getSourceEntry().getGroupSection().strip())
                    .distinct().sorted().toList();
            result.add(new ReviewEligibilityResponse(key.offeringId(), offering.getCourse().getId(),
                    offering.getTeacher().getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                    offering.getTeacher().getName(), key.term(), sections));
        });
        result.sort(Comparator.comparing(ReviewEligibilityResponse::academicTerm)
                .thenComparing(ReviewEligibilityResponse::courseCode)
                .thenComparing(ReviewEligibilityResponse::teacherName)
                .thenComparing(ReviewEligibilityResponse::courseOfferingId));
        return List.copyOf(result);
    }

    public boolean canReview(String authenticatedEmail, Long courseOfferingId, String academicTerm) {
        return list(authenticatedEmail).stream().anyMatch(e ->
                e.courseOfferingId().equals(courseOfferingId) && e.academicTerm().equals(academicTerm));
    }

    /** Future submission must call this again; a frontend indicator grants no authority. */
    public void requireReviewEligibility(String authenticatedEmail, Long courseOfferingId, String academicTerm) {
        if (!canReview(authenticatedEmail, courseOfferingId, academicTerm))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No verified schedule relationship for this target.");
    }

    private boolean consistentEvidence(VerifiedCourse row) {
        var entry = row.getSourceEntry();
        var offering = row.getCourseOffering();
        if (java.util.stream.Stream.of(entry.getCourseCode(), entry.getCourseName(), entry.getTeacherName(),
                entry.getGroupSection(), entry.getAcademicTerm()).anyMatch(s -> normalize(s).isEmpty())) return false;
        // Check only the already-linked evidence for contradictions, not other catalog candidates.
        return normalize(entry.getCourseCode()).equals(normalize(offering.getCourse().getCode()))
                && normalize(entry.getCourseName()).equals(normalize(offering.getCourse().getName()))
                && normalize(entry.getTeacherName()).equals(normalize(offering.getTeacher().getName()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record Target(Long offeringId, String term) {}
}
