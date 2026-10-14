package kz.sdu.feedback.review.sentiment;

import java.util.List;

public interface SentimentProvider {
    enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    // Exactly one classification per anonymous text, in input order. No moderation decisions.
    List<Sentiment> classify(List<String> texts);
}
