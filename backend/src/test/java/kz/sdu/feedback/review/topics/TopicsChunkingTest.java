package kz.sdu.feedback.review.topics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import kz.sdu.feedback.gemini.GeminiTransport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TopicsChunkingTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static String envelope(Object result) {
        var content = Map.of("parts", List.of(Map.of("text", JSON.writeValueAsString(result))));
        return JSON.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content", content))));
    }
    static List<String> texts() { return java.util.stream.IntStream.range(0,60).mapToObj(i->i+":"+"x".repeat(1900)).toList(); }

    @Test void singletonThemesAcrossDifferentChunksCanRecurInWholeDataset() {
        var transport=mock(GeminiTransport.class); var seen=new ArrayList<String>();
        when(transport.generate(anyString())).thenAnswer(call->{
            String body=call.getArgument(0); assertTrue(GeminiTransport.fits(body));
            var root=JSON.readTree(body); var input=JSON.readTree(root.at("/contents/0/parts/0/text").asText());
            if(input.get(0).isString()) {
                assertTrue(root.at("/systemInstruction/parts/0/text").asText().contains("single-item"));
                var items=new ArrayList<Object>();
                for(var item:input) {
                    seen.add(item.asText()); int index=Integer.parseInt(item.asText().split(":",2)[0]);
                    items.add(Map.of("topics", index==0 ? List.of("Clear explanations") : index==59 ? List.of("Teaching clarity") : List.of(),"concerns",List.of()));
                }
                return envelope(Map.of("items",items));
            }
            assertEquals(2,input.size());
            for(var item:input) { assertEquals(Set.of("candidate","kind","label","support"),item.propertyNames()); assertEquals(1,item.get("support").asInt()); }
            return envelope(Map.of("groups",List.of(Map.of("kind","TOPIC","label","Clear explanations","members",List.of(0,1)))));
        });
        var result=new GeminiTopicsProvider(transport).analyze(texts());
        assertEquals(List.of("Clear explanations"),result.topics()); assertEquals(texts(),seen);
        verify(transport,never()).close();
    }
    @Test void overlappingSynonymsFromOneReviewDoNotInventRecurrence() {
        var transport=mock(GeminiTransport.class);
        when(transport.generate(anyString())).thenAnswer(call->{
            var input=JSON.readTree(JSON.readTree(call.<String>getArgument(0)).at("/contents/0/parts/0/text").asText());
            if(input.get(0).isString()) {
                var items=new ArrayList<Object>();
                for(var item:input) items.add(Map.of("topics",item.asText().startsWith("0:") ? List.of("Clarity","Clear explanations") : List.of(),"concerns",List.of()));
                return envelope(Map.of("items",items));
            }
            return envelope(Map.of("groups",List.of(Map.of("kind","TOPIC","label","Clarity","members",List.of(0,1)))));
        });
        assertThrows(IllegalStateException.class,()->new GeminiTopicsProvider(transport).analyze(texts()));
    }
    @Test void missingMapItemsFailInsteadOfDroppingReviews() {
        var transport=mock(GeminiTransport.class); when(transport.generate(anyString())).thenReturn(envelope(Map.of("items",List.of())));
        assertThrows(IllegalStateException.class,()->new GeminiTopicsProvider(transport).analyze(texts()));
    }
    @Test void smallDatasetUsesOriginalTopicsContractOnce() {
        var transport=mock(GeminiTransport.class); var texts=List.of("clear","clear","okay");
        when(transport.generate(anyString())).thenReturn(envelope(Map.of("topics",List.of("Clarity"),"concerns",List.of())));
        assertEquals(List.of("Clarity"),new GeminiTopicsProvider(transport).analyze(texts).topics());
        verify(transport).generate(GeminiTopicsProvider.requestBody(texts));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void intermediateReductionMustCoverEveryCandidate(boolean omitCandidate) {
        var transport=mock(GeminiTransport.class); int[] intermediateCalls={0};
        var texts=java.util.stream.IntStream.range(0,128).mapToObj(i->i+":"+"x".repeat(1900)).toList();
        var seen=new ArrayList<String>();
        when(transport.generate(anyString())).thenAnswer(call->{
            String body=call.getArgument(0); assertTrue(GeminiTransport.fits(body));
            var root=JSON.readTree(body); var input=JSON.readTree(root.at("/contents/0/parts/0/text").asText());
            if(input.get(0).isString()) {
                var items=new ArrayList<Object>();
                for(var item:input) {
                    seen.add(item.asText()); String id=item.asText().split(":",2)[0];
                    var topics=new ArrayList<String>(); var concerns=new ArrayList<String>();
                    for(int i=0;i<5;i++) { topics.add("T"+id+"-"+i+"学".repeat(60)); concerns.add("C"+id+"-"+i+"学".repeat(60)); }
                    items.add(Map.of("topics",topics,"concerns",concerns));
                }
                return envelope(Map.of("items",items));
            }
            boolean intermediate=root.at("/systemInstruction/parts/0/text").asText().contains("intermediate consolidation");
            if(intermediate) intermediateCalls[0]++;
            var groups=new ArrayList<Object>();
            for(String kind:List.of("TOPIC","CONCERN")) {
                var members=new ArrayList<Integer>();
                for(var item:input) if(item.get("kind").asText().equals(kind)) members.add(item.get("candidate").asInt());
                if(intermediate && omitCandidate && !members.isEmpty()) members.removeFirst();
                if(!members.isEmpty()) groups.add(Map.of("kind",kind,"label",kind.equals("TOPIC")?"Clarity":"Workload","members",members));
            }
            return envelope(Map.of("groups",groups));
        });
        if(omitCandidate) assertThrows(IllegalStateException.class,()->new GeminiTopicsProvider(transport).analyze(texts));
        else {
            var result=new GeminiTopicsProvider(transport).analyze(texts);
            assertEquals(List.of("Clarity"),result.topics()); assertEquals(List.of("Workload"),result.concerns());
        }
        assertTrue(intermediateCalls[0]>0); assertEquals(texts,seen);
    }
}
