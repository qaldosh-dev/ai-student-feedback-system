package kz.sdu.feedback.auth;

import static org.junit.jupiter.api.Assertions.*;
import kz.sdu.feedback.auth.service.RegistrationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class RegistrationPolicyTest {
    private final RegistrationPolicy policy = new RegistrationPolicy();

    @Test void normalizesBothAcceptedSduDomains() {
        assertEquals("student@stu.sdu.edu.kz", policy.normalizeEmail("  STUDENT@Stu.Sdu.Edu.Kz  "));
        assertEquals("person@sdu.edu.kz", policy.normalizeEmail("  PERSON@SDU.EDU.KZ  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@gmail.com", "user@mail.ru", "user@yahoo.com", "user@outlook.com",
            "user@feedback.test", "user@sdu.edu.kz.fake.com", "user@fake-sdu.edu.kz", "user@sub.sdu.edu.kz",
            "user", "@sdu.edu.kz", "a@@sdu.edu.kz", "a b@sdu.edu.kz", "a..b@sdu.edu.kz",
            ".user@sdu.edu.kz", "user.@sdu.edu.kz", "user@sdu.edu.kz\nBcc: x", ""})
    void rejectsOtherDomainsAndMalformedAddresses(String email) {
        assertThrows(ResponseStatusException.class, () -> policy.normalizeEmail(email));
    }

    @Test void rejectsMissingOrOverlongMailbox() {
        assertThrows(ResponseStatusException.class, () -> policy.normalizeEmail(null));
        assertThrows(ResponseStatusException.class, () -> policy.normalizeEmail("a".repeat(65) + "@sdu.edu.kz"));
    }

    @Test void passwordChecksCharactersAndBcryptByteLimit() {
        assertDoesNotThrow(() -> policy.validatePassword("a".repeat(8)));
        assertDoesNotThrow(() -> policy.validatePassword("a".repeat(72)));
        assertDoesNotThrow(() -> policy.validatePassword("я".repeat(36)));
        assertDoesNotThrow(() -> policy.validatePassword("  valid password  "));
        for (String input : new String[]{null, "short", "        ", " \t\n\r    ", "\u2003".repeat(8), "a".repeat(73), "я".repeat(37), "1234567\0"}) {
            assertThrows(ResponseStatusException.class, () -> policy.validatePassword(input));
        }
    }
}
