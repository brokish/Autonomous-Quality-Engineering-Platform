package io.aqep.core.execution;

import io.aqep.healing.LocatorRecipe;
import java.util.Optional;

/** Suggests a semantic replacement for a locator that no longer resolves. */
@FunctionalInterface
public interface LocatorHealer {
    Optional<LocatorRecipe> suggest(String failedLocator, String domSnapshot);
}
