package com.fixflow.commons.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hides generated one-phone placeholders after an imported family has superseded them.
 *
 * <p>Containment alone is not enough: two real variants can fit the same phones. Callers must
 * explicitly identify both the imported canonical families and generated placeholders.
 */
public final class SubsumedComponents {

    private SubsumedComponents() {
    }

    public static Set<UUID> subsumed(Map<UUID, String> categoryByComponent,
                                     Map<UUID, Set<UUID>> devicesByComponent,
                                     Set<UUID> canonicalImports,
                                     Set<UUID> generatedPlaceholders) {
        Set<UUID> drop = new HashSet<>();
        for (UUID placeholder : generatedPlaceholders) {
            String category = categoryByComponent.get(placeholder);
            Set<UUID> devices = devicesByComponent.getOrDefault(placeholder, Set.of());
            boolean covered = !devices.isEmpty() && canonicalImports.stream().anyMatch(canonical ->
                    category != null
                            && category.equals(categoryByComponent.get(canonical))
                            && devicesByComponent.getOrDefault(canonical, Set.of()).containsAll(devices));
            if (covered) {
                drop.add(placeholder);
            }
        }
        return drop;
    }
}
