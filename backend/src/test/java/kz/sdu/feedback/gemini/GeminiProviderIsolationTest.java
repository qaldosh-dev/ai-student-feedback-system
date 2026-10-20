package kz.sdu.feedback.gemini;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.sentiment.*;
import kz.sdu.feedback.review.topics.*;
import kz.sdu.feedback.review.summary.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiProviderIsolationTest {
    @Test void fourIndependentProvidersShareTransportAndOneFailureDoesNotContaminateOthers() {
        var json=JsonMapper.builder().build(); var transport=mock(GeminiTransport.class);
        when(transport.generate(anyString())).thenAnswer(call->{
            String policy=json.readTree(call.<String>getArgument(0)).at("/systemInstruction/parts/0/text").asText();
            Object result;
            if(policy.contains("Write a concise neutral aggregate summary")) throw new GeminiTransport.Failure("TIMEOUT",null,"REQUEST_TIMED_OUT");
            if(policy.contains("Classify sentiment")) result=Map.of("sentiments",List.of("POSITIVE","NEUTRAL","NEGATIVE"));
            else if(policy.contains("Identify recurring")) result=Map.of("topics",List.of("Clarity"),"concerns",List.of());
            else result=Map.of("decision","ACCEPT","categories",List.of());
            return json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",
                    Map.of("parts",List.of(Map.of("text",json.writeValueAsString(result))))))));
        });
        var texts=List.of("Good","Okay","Confusing");
        assertThrows(IllegalStateException.class,()->new GeminiSummaryProvider(transport).summarize(texts));
        assertEquals(3,new GeminiSentimentProvider(transport).classify(texts).size());
        assertEquals(List.of("Clarity"),new GeminiTopicsProvider(transport).analyze(texts).topics());
        assertEquals(ModerationResult.Decision.ACCEPT,new GeminiModerationProvider(transport).moderate("Good").decision());
        verify(transport,times(4)).generate(anyString()); verify(transport,never()).close();
    }
}
