package kz.sdu.feedback.review.summary;

import java.util.List;

public interface SummaryProvider {
    SummaryResult summarize(List<String> publishedTexts);
}
