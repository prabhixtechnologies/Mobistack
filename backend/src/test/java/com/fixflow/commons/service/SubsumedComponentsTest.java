package com.fixflow.commons.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubsumedComponentsTest {

    @Test
    void dropsExactSpareCoveredBySharedFamily() {
        UUID exact = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID shared = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID phone = UUID.randomUUID();

        Set<UUID> drop = SubsumedComponents.subsumed(
                Map.of(exact, "DISPLAY_CONNECTOR", shared, "DISPLAY_CONNECTOR"),
                Map.of(exact, Set.of(phone), shared, Set.of(phone, UUID.randomUUID(), UUID.randomUUID())),
                Set.of(shared),
                Set.of(exact));

        assertThat(drop).containsExactly(exact);
    }

    @Test
    void keepsFamiliesThatEachFitADifferentPhone() {
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        UUID sharedPhone = UUID.randomUUID();

        Set<UUID> drop = SubsumedComponents.subsumed(
                Map.of(left, "DISPLAY_CONNECTOR", right, "DISPLAY_CONNECTOR"),
                Map.of(
                        left, Set.of(sharedPhone, UUID.randomUUID()),
                        right, Set.of(sharedPhone, UUID.randomUUID())),
                Set.of(right),
                Set.of(left));

        assertThat(drop).isEmpty();
    }

    @Test
    void doesNotCollapseDifferentPartTypes() {
        UUID connector = UUID.randomUUID();
        UUID battery = UUID.randomUUID();
        UUID phone = UUID.randomUUID();

        Set<UUID> drop = SubsumedComponents.subsumed(
                Map.of(connector, "DISPLAY_CONNECTOR", battery, "BATTERY"),
                Map.of(connector, Set.of(phone), battery, Set.of(phone, UUID.randomUUID())),
                Set.of(battery),
                Set.of(connector));

        assertThat(drop).isEmpty();
    }

    @Test
    void keepsUnclassifiedPartsEvenWhenTheirPhonesOverlap() {
        UUID smaller = UUID.randomUUID();
        UUID larger = UUID.randomUUID();
        UUID phone = UUID.randomUUID();

        Set<UUID> drop = SubsumedComponents.subsumed(
                Map.of(smaller, "DISPLAY_FOLDER", larger, "DISPLAY_FOLDER"),
                Map.of(smaller, Set.of(phone), larger, Set.of(phone, UUID.randomUUID())),
                Set.of(),
                Set.of());

        assertThat(drop).isEmpty();
    }
}
