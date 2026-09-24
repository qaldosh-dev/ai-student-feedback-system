package kz.sdu.feedback.auth.dto;

import java.util.Set;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;

public record CurrentUser(Long id, String email, Set<UserRole> roles,
                          boolean emailVerified, LinkedTeacher linkedTeacher) {
    public record LinkedTeacher(Long id, String name) { }

    public static CurrentUser from(User user) {
        var teacher = user.getLinkedTeacher();
        return new CurrentUser(user.getId(), user.getEmail(), user.getRoles(), user.isEmailVerified(),
                teacher == null ? null : new LinkedTeacher(teacher.getId(), teacher.getName()));
    }
}
