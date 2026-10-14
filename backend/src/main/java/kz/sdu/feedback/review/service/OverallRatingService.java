package kz.sdu.feedback.review.service;

import java.util.LinkedHashMap;
import kz.sdu.feedback.review.dto.OverallRatingResponse;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OverallRatingService {
    private final TeacherRepository teachers;
    private final ReviewRepository reviews;

    public OverallRatingService(TeacherRepository teachers, ReviewRepository reviews) {
        this.teachers = teachers;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public OverallRatingResponse overall(Long teacherId) {
        var teacher = teachers.findById(teacherId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found."));
        var row = reviews.findOverallRating(teacherId);
        var distribution = new LinkedHashMap<Integer, Long>();
        distribution.put(5, row.getFive()); distribution.put(4, row.getFour());
        distribution.put(3, row.getThree()); distribution.put(2, row.getTwo()); distribution.put(1, row.getOne());
        return new OverallRatingResponse(teacherId, teacher.getName(), row.getAverageRating(),
                row.getPublishedReviewCount(), distribution);
    }
}
