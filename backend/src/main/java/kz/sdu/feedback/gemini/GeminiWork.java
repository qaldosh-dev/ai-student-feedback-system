package kz.sdu.feedback.gemini;

import java.util.*;
import java.util.function.Function;

/** Per-analysis resource budget and deterministic byte-based partitioning, not an AI service. */
public final class GeminiWork {
    public static final int MAX_REVIEWS = 10_000;
    public static final int MAX_CALLS = 128;
    private static final long MAX_NANOS = java.time.Duration.ofMinutes(2).toNanos();
    private final GeminiTransport transport;
    private final long started = System.nanoTime();
    private int calls;

    public GeminiWork(GeminiTransport transport) { this.transport = transport; }

    public String call(String body) {
        if (++calls > MAX_CALLS || System.nanoTime() - started >= MAX_NANOS) throw limit();
        return transport.generate(body);
    }

    public static void validateTexts(List<String> texts) {
        if (texts.size() > MAX_REVIEWS) throw limit();
        // This is the existing product maximum, not truncation. Protect direct/legacy calls too.
        if (texts.stream().anyMatch(t -> t == null || t.length() > 2000)) throw limit();
    }

    public static boolean fitsTexts(List<String> texts, Function<List<String>, String> body) {
        long characters = 0;
        for (String text : texts) characters += text.length();
        return characters <= GeminiTransport.MAX_REQUEST_BYTES && GeminiTransport.fits(body.apply(texts));
    }

    public static List<List<String>> textChunks(List<String> texts, Function<List<String>, String> body, int maxItems) {
        return partition(texts, items -> fitsTexts(items, body), maxItems);
    }

    public static <T> List<List<T>> chunks(List<T> items, Function<List<T>, String> body) {
        return partition(items, part -> GeminiTransport.fits(body.apply(part)), items.size());
    }

    private static <T> List<List<T>> partition(List<T> items, java.util.function.Predicate<List<T>> fits, int maxItems) {
        var result = new ArrayList<List<T>>();
        for (int start = 0; start < items.size();) {
            int low = 1, high = Math.min(maxItems, items.size() - start), best = 0;
            // Serialized array size grows with each complete item. No review is split or omitted.
            while (low <= high) {
                int size = (low + high) / 2;
                if (fits.test(items.subList(start, start + size))) { best = size; low = size + 1; }
                else high = size - 1;
            }
            if (best == 0 || result.size() >= MAX_CALLS) throw limit();
            result.add(List.copyOf(items.subList(start, start + best))); start += best;
        }
        return List.copyOf(result);
    }

    public static GeminiTransport.Failure limit() {
        return new GeminiTransport.Failure("WORK_LIMIT", null, "ANALYTICS_SAFETY_LIMIT");
    }
}
