package kz.sdu.feedback.account.service;

import kz.sdu.feedback.auth.dto.CurrentUser;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountSetupService {
    private final UserRepository users;
    private final TeacherRepository teachers;

    public AccountSetupService(UserRepository users, TeacherRepository teachers) {
        this.users = users;
        this.teachers = teachers;
    }

    @Transactional
    public CurrentUser selectStudent(String email) {
        var user = verifiedUserForUpdate(email);
        user.selectStudentRole();
        users.flush();
        return CurrentUser.from(user);
    }

    @Transactional
    public CurrentUser claimTeacher(String email, Long teacherId) {
        var user = verifiedUserForUpdate(email);
        if (teacherId == null || teacherId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing teacher profile.");
        }
        if (user.getLinkedTeacher() != null) {
            if (!user.getLinkedTeacher().getId().equals(teacherId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your linked teacher profile cannot be changed.");
            }
            user.claimTeacher(user.getLinkedTeacher());
        } else {
            // Serialize competing claims for one profile; the unique FK remains the final safeguard.
            var teacher = teachers.findByIdForUpdate(teacherId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher profile not found."));
            if (users.existsByLinkedTeacherId(teacherId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This teacher profile has already been claimed.");
            }
            user.claimTeacher(teacher);
        }
        users.flush();
        return CurrentUser.from(user);
    }

    private User verifiedUserForUpdate(String email) {
        var user = users.findByEmailForUpdate(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required."));
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before completing account setup.");
        }
        return user;
    }
}
