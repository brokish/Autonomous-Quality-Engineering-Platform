package io.aqep.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public final class SemanticLocatorFactory {
    public Locator byRole(Page page, AriaRole role, String accessibleName) {
        return page.getByRole(role, new Page.GetByRoleOptions().setName(accessibleName));
    }

    public Locator byLabel(Page page, String label) {
        return page.getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    public Locator byPlaceholder(Page page, String placeholder) {
        return page.getByPlaceholder(placeholder, new Page.GetByPlaceholderOptions().setExact(true));
    }
}
