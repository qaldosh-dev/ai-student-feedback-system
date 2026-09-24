package kz.sdu.feedback.auth.controller;

import java.util.Set;
import kz.sdu.feedback.auth.dto.CurrentUser;
import kz.sdu.feedback.auth.service.AccountDetailsService;
import kz.sdu.feedback.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleAreaController {
    private final AccountDetailsService accounts;

    public RoleAreaController(AccountDetailsService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/student/home")
    public AreaHome student(Authentication authentication) {
        return home(authentication, UserRole.STUDENT);
    }

    @GetMapping("/api/teacher/home")
    public AreaHome teacher(Authentication authentication) {
        return home(authentication, UserRole.TEACHER);
    }

    @GetMapping("/api/admin/home")
    public AreaHome admin(Authentication authentication) {
        return home(authentication, UserRole.ADMIN);
    }

    private AreaHome home(Authentication authentication, UserRole area) {
        var user = accounts.currentUser(authentication.getName());
        return new AreaHome(area, user.email(), user.roles(),
                area == UserRole.TEACHER ? user.linkedTeacher() : null);
    }

    public record AreaHome(UserRole area, String email, Set<UserRole> roles,
                           CurrentUser.LinkedTeacher linkedTeacher) { }
}
