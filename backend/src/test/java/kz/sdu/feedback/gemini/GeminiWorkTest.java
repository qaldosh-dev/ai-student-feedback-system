package kz.sdu.feedback.gemini;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiWorkTest {
    @Test void completeItemsAppearExactlyOnceInDeterministicByteSizedChunks() {
        var texts = java.util.stream.IntStream.range(0, 200).mapToObj(i -> i + ":" + "Қ😀\\\"".repeat(200)).toList();
        var json = JsonMapper.builder().build();
        java.util.function.Function<List<String>,String> body = part -> json.writeValueAsString(Map.of("data", json.writeValueAsString(part)));
        var chunks = GeminiWork.textChunks(texts, body, texts.size());
        assertTrue(chunks.size() > 1);
        assertEquals(texts, chunks.stream().flatMap(List::stream).toList());
        for (var chunk : chunks) assertTrue(GeminiTransport.fits(body.apply(chunk)));
        assertEquals(chunks, GeminiWork.textChunks(texts, body, texts.size()));
    }
    @Test void reviewAndCallCeilingsAreExplicitAndDoNotSample() {
        assertThrows(GeminiTransport.Failure.class, () -> GeminiWork.validateTexts(Collections.nCopies(GeminiWork.MAX_REVIEWS + 1,"ok")));
        assertThrows(GeminiTransport.Failure.class, () -> GeminiWork.validateTexts(List.of("x".repeat(2001))));
        var transport = mock(GeminiTransport.class); var work = new GeminiWork(transport);
        for (int i=0;i<GeminiWork.MAX_CALLS;i++) work.call("{}");
        assertThrows(GeminiTransport.Failure.class, () -> work.call("{}"));
        verify(transport,times(GeminiWork.MAX_CALLS)).generate("{}");
    }
}
