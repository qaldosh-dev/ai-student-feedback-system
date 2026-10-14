package kz.sdu.feedback.review.dto;

import java.util.Map;

public record OverallRatingResponse(Long teacherId, String teacherName, Double averageRating,
                                    long publishedReviewCount, Map<Integer, Long> ratingDistribution) { }
