package kz.sdu.feedback.review.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import kz.sdu.feedback.auth.service.AccountDetailsService;
import kz.sdu.feedback.auth.dto.CurrentUser;
import kz.sdu.feedback.review.dto.TeacherFeedbackResponse;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherFeedbackService {
    private final AccountDetailsService accounts;
    private final ReviewRepository reviews;

    public TeacherFeedbackService(AccountDetailsService accounts, ReviewRepository reviews) {
        this.accounts = accounts;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public List<TeacherFeedbackResponse> feedback(String authenticatedEmail) {
        CurrentUser account;
        try { account = accounts.currentUser(authenticatedEmail); }
        catch (UsernameNotFoundException missing) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required.");
        }
        if (account.linkedTeacher() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No teacher profile is linked to this account.");

        var groups = new LinkedHashMap<GroupKey, TeacherFeedbackResponse>();
        for (var row : reviews.findPublishedForTeacher(account.linkedTeacher().id())) {
            var key = new GroupKey(row.getCourseOfferingId(), row.getAcademicTerm());
            var group = groups.computeIfAbsent(key, ignored -> new TeacherFeedbackResponse(row.getCourseOfferingId(),
                    row.getCourseCode(), row.getCourseName(), row.getAcademicTerm(), new ArrayList<>()));
            group.feedback().add(new TeacherFeedbackResponse.Feedback(row.getRating(), row.getText(), row.getPublishedAt()));
        }
        return List.copyOf(groups.values());
    }

    private record GroupKey(Long offeringId, String academicTerm) { }
}
