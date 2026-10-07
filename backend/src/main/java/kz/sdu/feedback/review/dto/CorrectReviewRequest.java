package kz.sdu.feedback.review.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CorrectReviewRequest(String text) {
    public static CorrectReviewRequest fromJson(JsonNode input) {
        if (!input.isObject() || !input.propertyNames().equals(Set.of("text")) || !input.get("text").isString())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send only feedback text.");
        return new CorrectReviewRequest(input.get("text").stringValue());
    }
}
