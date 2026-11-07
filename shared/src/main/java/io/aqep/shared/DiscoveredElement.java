package io.aqep.shared;

/** Bounded description of a navigational or interactive page element. */
public record DiscoveredElement(Kind kind, String label, String locatorHint, String destination, String formMethod) {
    public enum Kind { LINK, BUTTON, FORM, INPUT, SEARCH, DOWNLOAD }

    public DiscoveredElement {
        if (kind == null) throw new IllegalArgumentException("Element kind is required");
        label = bounded(label, 300);
        locatorHint = bounded(locatorHint, 500);
        destination = bounded(destination, 2_000);
        formMethod = bounded(formMethod, 20);
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
