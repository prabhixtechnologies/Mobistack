package com.fixflow.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Java mirror of the {@code fixflow_normalize(text)} SQL function from
 * migration V2.
 *
 * <p>Search terms are normalised here before being sent to the database so they
 * compare like-for-like against the generated {@code normalized_*} columns.
 * Both sides must stay in step: "Realme&nbsp;6i", "realme-6i" and "REALME 6I"
 * all have to collapse to {@code "realme 6i"}.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutAccents = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String lowered = withoutAccents.toLowerCase(java.util.Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(lowered).replaceAll(" ").trim();
    }

    /** Returns null for blank input so JPQL "is null" checks skip the filter. */
    public static String normalizeOrNull(String input) {
        String normalized = normalize(input);
        return normalized.isEmpty() ? null : normalized;
    }

    /** Uppercased, punctuation-free form used for SKU comparison. */
    public static String normalizeSku(String sku) {
        if (sku == null) {
            return null;
        }
        return sku.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Builds a stable group code such as {@code REALME_DISPLAY_GROUP_001}.
     */
    public static String toCode(String input) {
        return normalize(input).replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
    }
}
