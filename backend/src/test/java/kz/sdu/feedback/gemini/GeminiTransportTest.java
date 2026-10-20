package kz.sdu.feedback.gemini;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class GeminiTransportTest {
    HttpServer server;
    HttpClient actual;
    HttpClient routed;
    ExecutorService executor;
    GeminiTransport transport;
    volatile int bytes = 4, status = 200, delay;
    volatile boolean chunked;
    AtomicInteger requests = new AtomicInteger();

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            try {
                exchange.sendResponseHeaders(status, chunked ? 0 : bytes);
                if (delay > 0) Thread.sleep(delay);
                byte[] block = new byte[1024]; Arrays.fill(block, (byte)'a');
                for (int written = 0; written < bytes;) {
                    int length = Math.min(block.length, bytes - written);
                    exchange.getResponseBody().write(block, 0, length); written += length;
                }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
              catch (java.io.IOException cancelled) { /* Client cancelled an oversized/slow body. */ }
            finally { exchange.close(); }
        });
        server.start(); actual = HttpClient.newHttpClient(); routed = mock(HttpClient.class);
        // Keep production endpoint immutable. Route only synthetic test requests to a local fixture.
        when(routed.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest original = call.getArgument(0);
            assertEquals("https", original.uri().getScheme());
            assertEquals("synthetic-key", original.headers().firstValue("x-goog-api-key").orElseThrow());
            var local = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                    .timeout(original.timeout().orElseThrow()).POST(original.bodyPublisher().orElseThrow()).build();
            return actual.sendAsync(local, call.<HttpResponse.BodyHandler<String>>getArgument(1));
        });
        transport = new GeminiTransport("synthetic-key", "gemini-test", 1000, routed);
    }
    @AfterEach void close() {
        transport.close(); server.stop(0); executor.shutdownNow(); actual.close();
    }

    @Test void repeatedAndConcurrentRequestsReuseClientWithoutClosingIt() {
        assertEquals("aaaa", transport.generate("{}"));
        var first = CompletableFuture.supplyAsync(() -> transport.generate("{}"));
        var second = CompletableFuture.supplyAsync(() -> transport.generate("{}"));
        assertEquals("aaaa", first.join()); assertEquals("aaaa", second.join());
        assertEquals(3, requests.get()); verify(routed, never()).close();
        transport.close(); verify(routed).close();
    }
    @Test void finalUtf8RequestBoundaryAndEscapes() {
        for (String text : List.of("Қазақша", "Кириллица", "😀", "\\\"", "\u0001")) {
            String prefix = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            String exact = prefix + " ".repeat(GeminiTransport.MAX_REQUEST_BYTES - prefix.getBytes(StandardCharsets.UTF_8).length);
            assertTrue(GeminiTransport.fits(exact)); assertEquals("aaaa", transport.generate(exact));
            var failure = assertThrows(GeminiTransport.Failure.class, () -> transport.generate(exact + "a"));
            assertEquals("REQUEST_TOO_LARGE", failure.reason);
        }
        assertEquals(5, requests.get());
    }
    @Test void responseExactlyAtLimitWorks() {
        bytes = GeminiTransport.MAX_RESPONSE_BYTES;
        assertEquals(bytes, transport.generate("{}").getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void oversizedContentLengthAndChunkedBodiesFailWithoutPoisoningNextRequest() {
        for (boolean streaming : List.of(false, true)) {
            chunked = streaming; bytes = GeminiTransport.MAX_RESPONSE_BYTES + 1;
            var failure = assertThrows(GeminiTransport.Failure.class, () -> transport.generate("{}"));
            assertEquals("RESPONSE_TOO_LARGE", failure.reason); assertEquals(200, failure.status);
        }
        bytes = 4; assertEquals("aaaa", transport.generate("{}"));
    }
    @Test void oversizedErrorBodyIsAlsoBounded() {
        bytes = GeminiTransport.MAX_RESPONSE_BYTES + 1; status = 503; chunked = true;
        var failure = assertThrows(GeminiTransport.Failure.class, () -> transport.generate("{}"));
        assertEquals("RESPONSE_TOO_LARGE", failure.reason); assertEquals(503, failure.status);
    }
    @Test void slowBodyAfterHeadersStillTimesOutAndCancels() {
        transport = new GeminiTransport("synthetic-key", "gemini-test", 100, routed); delay = 1500;
        long start = System.nanoTime();
        assertEquals("TIMEOUT", assertThrows(GeminiTransport.Failure.class, () -> transport.generate("{}")).category);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1200);
    }
    @Test void subscriberCancelsBeforeAccumulatingOverLimitBuffer() throws Exception {
        var subscriber = new GeminiTransport.BoundedBody(200);
        var subscription = mock(Flow.Subscription.class); subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[GeminiTransport.MAX_RESPONSE_BYTES])));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[1])));
        verify(subscription).cancel(); assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
