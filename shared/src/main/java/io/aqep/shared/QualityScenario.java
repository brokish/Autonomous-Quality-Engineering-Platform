package io.aqep.shared;

/** A bounded AI-proposed scenario; proposals are not executed automatically. */
public record QualityScenario(String title, Category category, Priority priority, String rationale) {
    public enum Category { NAVIGATION, SEARCH, DOWNLOAD, FORM, ACCESSIBILITY, VISUAL }
    public enum Priority { HIGH, MEDIUM, LOW }

    public QualityScenario {
        title = bounded(title, 160);
        rationale = bounded(rationale, 500);
        if (title.isBlank() || category == null || priority == null) {
            throw new IllegalArgumentException("A scenario requires a title, category and priority");
        }
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
