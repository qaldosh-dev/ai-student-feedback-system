package kz.sdu.feedback.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationConfiguration {
    @Bean
    Clock verificationClock() { return Clock.systemUTC(); }
}
