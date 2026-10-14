package kz.sdu.feedback.review.moderation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.net.http.*;
import java.util.stream.Collectors;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeminiDiagnosticsTest {
    private static final String SECRET = "synthetic-private-key";
    private static final String FEEDBACK = "private-feedback-never-log";
    private final Logger logger = (Logger) LoggerFactory.getLogger(GeminiModerationProvider.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private HttpClient http;
    @BeforeEach void setup() { appender.start(); logger.addAppender(appender); http = mock(HttpClient.class); }
    @AfterEach void cleanup() {
        assertFalse(logs().contains(SECRET)); assertFalse(logs().contains(FEEDBACK));
        assertFalse(logs().contains("Authorization"));
        assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        logger.detachAppender(appender); appender.stop(); Thread.interrupted();
    }
    String logs() { return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n")); }
    GeminiModerationProvider provider(String key, String model) { return new GeminiModerationProvider(key, model, 8000, () -> http); }
    @SuppressWarnings("unchecked")
    void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void startupReportsPresenceOnlyAndMissingKeyMakesNoNetworkRequest() {
        assertThrows(IllegalStateException.class, () -> provider("", "gemini-test").moderate(FEEDBACK));
        assertTrue(logs().contains("apiKeyPresent=false")); assertTrue(logs().contains("API_KEY_MISSING")); verifyNoInteractions(http);
    }
    @Test void malformedConfigurationIsSafeAndDoesNotSend() {
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "bad\r\n" + SECRET).moderate(FEEDBACK));
        assertTrue(logs().contains("category=INVALID_MODEL")); assertTrue(logs().contains("<invalid-or-redacted>")); verifyNoInteractions(http);
    }
    @ParameterizedTest @CsvSource({
        "404,NOT_FOUND,MODEL_UNAVAILABLE", "429,RESOURCE_EXHAUSTED,QUOTA_OR_RATE_LIMIT",
        "403,PERMISSION_DENIED,AUTHENTICATION_OR_PERMISSION", "401,UNAUTHENTICATED,AUTHENTICATION_OR_PERMISSION",
        "400,INVALID_ARGUMENT,HTTP_PROVIDER_ERROR", "503,UNAVAILABLE,HTTP_PROVIDER_ERROR"
    })
    void statusAndAllowlistedCodeAreLoggedWithoutProviderMessage(int status, String code, String category) throws Exception {
        response(status, "{\"error\":{\"status\":\"" + code + "\",\"message\":\"" + SECRET + " " + FEEDBACK + " Authorization\"}}");
        var error = assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        assertNull(error.getCause()); assertFalse(error.getMessage().contains(SECRET));
        assertTrue(logs().contains("category=" + category)); assertTrue(logs().contains("httpStatus=" + status));
        assertTrue(logs().contains("reason=" + code)); assertTrue(logs().contains("model=gemini-test"));
    }
    @Test void untrustedErrorCodeAndNonJsonHttpBodyAreNotLogged() throws Exception {
        response(500, "{\"error\":{\"status\":\"" + SECRET + "\"}}");
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        response(502, SECRET + FEEDBACK);
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        assertTrue(logs().contains("UNRECOGNIZED_PROVIDER_ERROR"));
    }
    @Test void timeoutAndNetworkAreDistinctWithoutRawExceptionMessages() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException(SECRET));
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        assertTrue(logs().contains("category=TIMEOUT"));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException(SECRET));
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        assertTrue(logs().contains("category=NETWORK"));
    }
    @Test void interruptedRequestRestoresInterruptFlag() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException(SECRET));
        assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
        assertTrue(Thread.currentThread().isInterrupted()); assertTrue(logs().contains("REQUEST_INTERRUPTED"));
    }
    @Test void parsingDiagnosticsDistinguishEnvelopeJsonResultJsonAndContract() throws Exception {
        for (String[] item : new String[][]{
                {SECRET, "INVALID_JSON", "ENVELOPE_JSON"},
                {GeminiModerationProviderTest.envelope(FEEDBACK), "INVALID_JSON", "RESULT_JSON"},
                {GeminiModerationProviderTest.envelope("{}"), "RESPONSE_PARSING", "RESULT_SCHEMA_OR_DECISION"},
                {"{}", "UNEXPECTED_PROVIDER_RESPONSE", "CANDIDATE_COUNT_INVALID"},
                {"{\"promptFeedback\":{\"blockReason\":\"" + SECRET + "\"}}", "UNEXPECTED_PROVIDER_RESPONSE", "PROMPT_BLOCKED"},
                {"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}", "UNEXPECTED_PROVIDER_RESPONSE", "OUTPUT_TRUNCATED"}
        }) {
            response(200, item[0]);
            assertThrows(IllegalStateException.class, () -> provider(SECRET, "gemini-test").moderate(FEEDBACK));
            assertTrue(logs().contains("category=" + item[1])); assertTrue(logs().contains("reason=" + item[2]));
        }
    }
    @Test void validResultIsUnchangedAndNotLogged() throws Exception {
        response(200, GeminiModerationProviderTest.envelope("{\"decision\":\"ACCEPT\",\"categories\":[]}"));
        assertEquals(ModerationResult.Decision.ACCEPT, provider(SECRET, "gemini-test").moderate(FEEDBACK).decision());
        assertFalse(logs().contains("failed")); assertFalse(logs().contains("ACCEPT"));
    }
}
