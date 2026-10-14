package kz.sdu.feedback.review.topics;

import java.util.List;

public interface TopicsProvider {
    TopicsResult analyze(List<String> publishedTexts);
}
