package kz.sdu.feedback.review.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record UpdateReviewRequest(Integer rating, String text) {
    public static UpdateReviewRequest fromJson(JsonNode input) {
        if (!input.isObject() || !input.propertyNames().equals(Set.of("rating", "text"))
                || !input.get("rating").isIntegralNumber() || !input.get("rating").canConvertToInt()
                || !input.get("text").isString())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send only an integer rating and feedback text.");
        return new UpdateReviewRequest(input.get("rating").intValue(), input.get("text").stringValue());
    }
}
