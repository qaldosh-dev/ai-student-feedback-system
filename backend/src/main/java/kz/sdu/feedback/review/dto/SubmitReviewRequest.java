package kz.sdu.feedback.review.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record SubmitReviewRequest(Long courseOfferingId, String academicTerm, Integer rating, String text) {
    // Inspect JSON types explicitly: Jackson must not coerce 4.5 or "4" into integer 4.
    public static SubmitReviewRequest fromJson(JsonNode input) {
        if (!input.isObject() || !input.propertyNames().equals(Set.of("courseOfferingId", "academicTerm", "rating", "text")))
            throw invalid("Send only courseOfferingId, academicTerm, rating and text.");
        var id = input.get("courseOfferingId");
        var rating = input.get("rating");
        if (!id.isIntegralNumber() || !id.canConvertToLong()) throw invalid("Invalid course offering.");
        if (!rating.isIntegralNumber() || !rating.canConvertToInt()) throw invalid("Rating must be between 1 and 5.");
        if (!input.get("academicTerm").isString()) throw invalid("Academic term is required.");
        if (!input.get("text").isString()) throw invalid("Feedback is required.");
        return new SubmitReviewRequest(id.longValue(), input.get("academicTerm").stringValue(), rating.intValue(), input.get("text").stringValue());
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
