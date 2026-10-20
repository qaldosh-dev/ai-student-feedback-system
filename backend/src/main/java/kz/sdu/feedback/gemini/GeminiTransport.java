package kz.sdu.feedback.gemini;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Shared HTTP infrastructure only. Feature prompts and result parsing stay in providers. */
@Component
public class GeminiTransport {
    public static final int MAX_REQUEST_BYTES = 64 * 1024;
    public static final int MAX_RESPONSE_BYTES = 100_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    @Autowired
    public GeminiTransport(@Value("${app.moderation.gemini.api-key:}") String apiKey,
            @Value("${app.moderation.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${app.moderation.gemini.timeout-ms:8000}") long timeoutMillis) {
        this(apiKey, model, timeoutMillis, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public GeminiTransport(String apiKey, String model, long timeoutMillis, HttpClient client) {
        this.apiKey = apiKey; this.model = model; this.client = client;
        timeout = Duration.ofMillis(Math.clamp(timeoutMillis, 100, 10000));
    }

    public boolean hasKey() { return apiKey != null && !apiKey.isBlank(); }
    public String safeModel() {
        return model != null && model.matches("[A-Za-z0-9._-]{1,100}")
                && (!hasKey() || !model.contains(apiKey)) ? model : "<invalid-or-redacted>";
    }
    public long timeoutMillis() { return timeout.toMillis(); }
    public static boolean fits(String body) {
        return body.length() <= MAX_REQUEST_BYTES && body.getBytes(StandardCharsets.UTF_8).length <= MAX_REQUEST_BYTES;
    }

    public String generate(String body) {
        if (!hasKey()) throw new Failure("MISSING_CONFIG", null, "API_KEY_MISSING");
        if (model == null || !model.matches("[A-Za-z0-9._-]{1,100}"))
            throw new Failure("INVALID_MODEL", null, "MODEL_NAME_INVALID");
        if (!fits(body)) throw new Failure("REQUEST_LIMIT", null, "REQUEST_TOO_LARGE");
        CompletableFuture<HttpResponse<String>> pending = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(timeout).header("Content-Type", "application/json").header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8))).build();
            pending = client.sendAsync(request, info -> new BoundedBody(info.statusCode()));
            // Deadline includes body completion, not just headers. Cancellation stops slow reads.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw httpFailure(response.statusCode(), response.body());
            return response.body();
        } catch (Failure safe) {
            throw safe;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure("NETWORK", null, "REQUEST_INTERRUPTED");
        } catch (TimeoutException timeoutFailure) {
            throw new Failure("TIMEOUT", null, "REQUEST_TIMED_OUT");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof Failure safe) throw safe;
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new Failure("NETWORK", null, "REQUEST_INTERRUPTED");
            }
            if (cause instanceof HttpTimeoutException) throw new Failure("TIMEOUT", null, "REQUEST_TIMED_OUT");
            throw new Failure("NETWORK", null, "CONNECTION_OR_IO_FAILED");
        } catch (RuntimeException unexpected) {
            throw new Failure("UNEXPECTED_PROVIDER_RESPONSE", null, "CLIENT_FAILURE");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private Failure httpFailure(int status, String body) {
        String code = "UNRECOGNIZED_PROVIDER_ERROR";
        try {
            String candidate = JSON.readTree(body).path("error").path("status").asText();
            if (Set.of("INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND",
                    "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "FAILED_PRECONDITION").contains(candidate)) code = candidate;
        } catch (RuntimeException ignored) { /* Never retain remote messages or bodies in exceptions. */ }
        String category = status == 429 || code.equals("RESOURCE_EXHAUSTED") ? "QUOTA_OR_RATE_LIMIT"
                : status == 404 || code.equals("NOT_FOUND") ? "MODEL_UNAVAILABLE"
                : status == 401 || status == 403 ? "AUTHENTICATION_OR_PERMISSION" : "HTTP_PROVIDER_ERROR";
        return new Failure(category, status, code);
    }

    @PreDestroy
    public void close() { client.close(); }

    public static final class Failure extends IllegalStateException {
        public final String category;
        public final Integer status;
        public final String reason;
        public Failure(String category, Integer status, String reason) {
            super("Gemini request did not complete safely.");
            this.category = category; this.status = status; this.reason = reason;
        }
    }

    static final class BoundedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int status;
        private Flow.Subscription subscription;
        BoundedBody(int status) { this.status = status; }
        public CompletionStage<String> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new Failure("RESPONSE_LIMIT", status, "RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { result.completeExceptionally(failure); }
        public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
    }
}
