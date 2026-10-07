package kz.sdu.feedback.review.moderation;

public enum ModerationCategory {
    PERSONAL_INSULT("Remove personal insults and focus on the learning experience."),
    THREAT("Remove threatening language."),
    HATE_OR_DISCRIMINATION("Remove hateful or discriminatory language."),
    PERSONAL_INFORMATION("Remove personal or private contact information."),
    SPAM_OR_IRRELEVANT("Keep feedback relevant to the course and learning experience."),
    ABUSIVE_LANGUAGE("Remove abusive language.");

    private final String message;
    ModerationCategory(String message) { this.message = message; }
    public String message() { return message; }
}
