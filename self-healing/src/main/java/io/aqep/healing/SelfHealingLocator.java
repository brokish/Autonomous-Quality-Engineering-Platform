package io.aqep.healing;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SelfHealingLocator {
    private final LocatorMemory memory;

    public SelfHealingLocator(LocatorMemory memory) {
        this.memory = memory;
    }

    public void click(Page page, String intentKey, Locator primary, List<LocatorRecipe> alternatives) throws IOException {
        if (isUniqueAndVisible(primary)) {
            primary.click();
            return;
        }

        List<LocatorRecipe> recipes = new ArrayList<>();
        memory.get(intentKey).ifPresent(recipes::add);
        recipes.addAll(alternatives);
        for (LocatorRecipe recipe : recipes) {
            Locator replacement = locate(page, recipe);
            if (isUniqueAndVisible(replacement)) {
                replacement.click();
                if (memory.get(intentKey).filter(recipe::equals).isEmpty()) memory.remember(intentKey, recipe);
                return;
            }
        }
        throw new PlaywrightException("No unique, visible semantic locator could be resolved for intent: " + intentKey);
    }

    private Locator locate(Page page, LocatorRecipe recipe) {
        return switch (recipe.strategy()) {
            case ROLE -> {
                String[] parts = recipe.value().split("\\|", 2);
                if (parts.length != 2) throw new IllegalArgumentException("ROLE recipe must be ROLE|accessible name");
                yield page.getByRole(AriaRole.valueOf(parts[0].toUpperCase()),
                        new Page.GetByRoleOptions().setName(parts[1]).setExact(true));
            }
            case LABEL -> page.getByLabel(recipe.value(), new Page.GetByLabelOptions().setExact(true));
            case PLACEHOLDER -> page.getByPlaceholder(recipe.value(),
                    new Page.GetByPlaceholderOptions().setExact(true));
            case TEXT -> page.getByText(recipe.value(), new Page.GetByTextOptions().setExact(true));
            case TEST_ID -> page.getByTestId(recipe.value());
        };
    }

    private boolean isUniqueAndVisible(Locator locator) {
        try {
            return locator.count() == 1 && locator.isVisible();
        } catch (PlaywrightException exception) {
            return false;
        }
    }
}
