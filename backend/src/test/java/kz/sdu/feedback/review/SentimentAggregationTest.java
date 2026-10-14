package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import kz.sdu.feedback.review.dto.TeacherAnalyticsResponse.CourseOption;
import kz.sdu.feedback.review.service.*;
import kz.sdu.feedback.review.sentiment.SentimentProvider;
import kz.sdu.feedback.review.sentiment.SentimentProvider.Sentiment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SentimentAggregationTest {
    @ParameterizedTest @CsvSource({
        "7,2,1,MOSTLY_POSITIVE,70,20,10", "1,7,2,MOSTLY_NEUTRAL,10,70,20", "1,2,7,MOSTLY_NEGATIVE,10,20,70",
        "4,2,4,MIXED,40,20,40", "1,1,1,MIXED,34,33,33", "2,2,0,MIXED,50,50,0", "0,2,2,MIXED,0,50,50",
        "3,0,0,MOSTLY_POSITIVE,100,0,0", "0,0,3,MOSTLY_NEGATIVE,0,0,100", "2,3,2,MOSTLY_NEUTRAL,29,43,28"
    })
    void countsDominanceAndLargestRemainder(int positive, int neutral, int negative, String overall, int pp, int np, int negp) {
        var analytics = mock(TeacherAnalyticsService.class);
        var provider = mock(SentimentProvider.class);
        int total = positive + neutral + negative;
        var labels = new ArrayList<Sentiment>();
        labels.addAll(Collections.nCopies(positive, Sentiment.POSITIVE)); labels.addAll(Collections.nCopies(neutral, Sentiment.NEUTRAL));
        labels.addAll(Collections.nCopies(negative, Sentiment.NEGATIVE));
        when(analytics.sentimentInput("owner", 1L, "term")).thenReturn(new TeacherAnalyticsService.SentimentInput(
                new CourseOption(1L, "ABC", "Subject", List.of("term")), "term", Collections.nCopies(total, "Anonymous text")));
        when(provider.classify(anyList())).thenReturn(labels);
        var result = new SentimentAnalysisService(analytics, provider).analyze("owner", 1L, "term");
        assertTrue(result.analysisAvailable()); assertEquals(total, result.analyzedReviewCount());
        assertEquals(positive, result.positiveCount()); assertEquals(neutral, result.neutralCount()); assertEquals(negative, result.negativeCount());
        assertEquals(overall, result.overallSentiment().name()); assertEquals(pp, result.positivePercentage());
        assertEquals(np, result.neutralPercentage()); assertEquals(negp, result.negativePercentage());
        assertEquals(100, result.positivePercentage() + result.neutralPercentage() + result.negativePercentage());
    }
}

