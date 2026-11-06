package io.aqep.healing;

public record LocatorRecipe(Strategy strategy, String value) {
    public enum Strategy { ROLE, LABEL, PLACEHOLDER, TEXT, TEST_ID }

    public LocatorRecipe {
        if (strategy == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("A locator recipe requires a supported strategy and non-empty value");
        }
    }

    public static LocatorRecipe role(String role, String name) {
        return new LocatorRecipe(Strategy.ROLE, role + "|" + name);
    }
}
