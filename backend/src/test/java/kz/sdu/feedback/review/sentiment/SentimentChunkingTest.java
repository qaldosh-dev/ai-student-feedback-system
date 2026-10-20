package kz.sdu.feedback.review.sentiment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import kz.sdu.feedback.gemini.GeminiTransport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SentimentChunkingTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    @Test void everyReviewGetsOneLabelInOrderAcrossChunks() {
        var texts = java.util.stream.IntStream.range(0,100).mapToObj(i -> i + ":" + "Қ😀\\\"".repeat(180)).toList();
        var seen = new ArrayList<String>();
        var transport = mock(GeminiTransport.class);
        when(transport.generate(anyString())).thenAnswer(call -> {
            String body = call.getArgument(0); assertTrue(GeminiTransport.fits(body));
            var input = JSON.readTree(JSON.readTree(body).at("/contents/0/parts/0/text").asText());
            var labels = new ArrayList<String>();
            for (var item : input) {
                seen.add(item.asText());
                labels.add(Integer.parseInt(item.asText().split(":",2)[0]) % 2 == 0 ? "POSITIVE" : "NEGATIVE");
            }
            return GeminiSentimentProviderTest.envelope(JSON.writeValueAsString(Map.of("sentiments",labels)));
        });
        var result = new GeminiSentimentProvider(transport).classify(texts);
        assertEquals(texts,seen); assertEquals(100,result.size());
        for(int i=0;i<result.size();i++) assertEquals(i%2==0 ? SentimentProvider.Sentiment.POSITIVE : SentimentProvider.Sentiment.NEGATIVE,result.get(i));
        verify(transport,atLeast(2)).generate(anyString()); verify(transport,never()).close();
        var analytics = mock(kz.sdu.feedback.review.service.TeacherAnalyticsService.class);
        when(analytics.sentimentInput("teacher", 1L, "term")).thenReturn(new kz.sdu.feedback.review.service.TeacherAnalyticsService.SentimentInput(
                new kz.sdu.feedback.review.dto.TeacherAnalyticsResponse.CourseOption(1L,"ABC","Course",List.of("term")),"term",texts));
        var aggregate = new kz.sdu.feedback.review.service.SentimentAnalysisService(analytics,new GeminiSentimentProvider(transport)).analyze("teacher",1L,"term");
        assertEquals(100,aggregate.analyzedReviewCount()); assertEquals(50,aggregate.positiveCount()); assertEquals(50,aggregate.negativeCount());
        assertEquals(50,aggregate.positivePercentage()); assertEquals(50,aggregate.negativePercentage());
        assertEquals("MIXED",aggregate.overallSentiment().name());
    }
    @Test void smallDatasetRetainsOneRequestAndOriginalBody() {
        var transport=mock(GeminiTransport.class); var texts=List.of("good","okay","bad");
        when(transport.generate(anyString())).thenReturn(GeminiSentimentProviderTest.envelope("{\"sentiments\":[\"POSITIVE\",\"NEUTRAL\",\"NEGATIVE\"]}"));
        assertEquals(3,new GeminiSentimentProvider(transport).classify(texts).size());
        verify(transport).generate(GeminiSentimentProvider.requestBody(texts));
    }
    @Test void failedOrIncompleteChunkDoesNotReturnPartialResults() {
        var transport=mock(GeminiTransport.class);
        when(transport.generate(anyString())).thenReturn(GeminiSentimentProviderTest.envelope("{\"sentiments\":[]}"));
        assertThrows(IllegalStateException.class,()->new GeminiSentimentProvider(transport).classify(Collections.nCopies(80,"x".repeat(2000))));
    }
}
