package com.fixflow.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a shop-counter string such as {@code "Samsung A32 4g"} or
 * {@code "iPhone 11"} into brand, model name, and optional radio variant.
 *
 * <p>Shop brands win over the built-in dictionary so a workspace that files
 * Redmi separately from Xiaomi keeps that split. Common typos from the old
 * paste lists ({@code samaung}) are corrected before matching.
 */
public final class DeviceNameParser {

    public record ParsedDeviceName(String brand, String name, String variant) {
        public String display() {
            return DeviceLabels.display(brand, name, variant);
        }
    }

    private static final Pattern VARIANT_SUFFIX = Pattern.compile(
            "(?i)\\s+(4g\\+?|5g\\+?|lte|wi-?fi)$");

    private static final Map<String, String> VARIANT_CANONICAL = Map.of(
            "4g", "4G",
            "4g+", "4G",
            "5g", "5G",
            "5g+", "5G",
            "lte", "LTE",
            "wifi", "WiFi",
            "wi-fi", "WiFi",
            "wi fi", "WiFi"
    );

    /** Misspellings seen on the old universal-list paste dumps. */
    private static final Map<String, String> TYPOS = new LinkedHashMap<>();

    /**
     * Canonical brands plus aliases. Longer keys are tried first.
     * {@code iphone} / {@code galaxy} map to a brand but stay in the model name.
     */
    private static final List<BrandAlias> DICTIONARY = List.of(
            new BrandAlias("samsung", "Samsung", false),
            new BrandAlias("xiaomi", "Xiaomi", false),
            new BrandAlias("redmi", "Redmi", false),
            new BrandAlias("realme", "Realme", false),
            new BrandAlias("vivo", "Vivo", false),
            new BrandAlias("oppo", "Oppo", false),
            new BrandAlias("poco", "Poco", false),
            new BrandAlias("motorola", "Motorola", false),
            new BrandAlias("oneplus", "OnePlus", false),
            new BrandAlias("one plus", "OnePlus", false),
            new BrandAlias("infinix", "Infinix", false),
            new BrandAlias("tecno", "Tecno", false),
            new BrandAlias("nokia", "Nokia", false),
            new BrandAlias("google", "Google", false),
            new BrandAlias("honor", "Honor", false),
            new BrandAlias("iqoo", "iQOO", false),
            new BrandAlias("nothing", "Nothing", false),
            new BrandAlias("asus", "Asus", false),
            new BrandAlias("lava", "Lava", false),
            new BrandAlias("micromax", "Micromax", false),
            new BrandAlias("huawei", "Huawei", false),
            new BrandAlias("sony", "Sony", false),
            new BrandAlias("lenovo", "Lenovo", false),
            new BrandAlias("itel", "Itel", false),
            new BrandAlias("apple", "Apple", false),
            new BrandAlias("iphone", "Apple", true),
            new BrandAlias("galaxy", "Samsung", true),
            new BrandAlias("moto", "Motorola", true)
    );

    static {
        TYPOS.put("samaung", "samsung");
        TYPOS.put("samung", "samsung");
        TYPOS.put("samsumg", "samsung");
        TYPOS.put("sansung", "samsung");
        TYPOS.put("xiaome", "xiaomi");
        TYPOS.put("xiomi", "xiaomi");
        TYPOS.put("redme", "redmi");
        TYPOS.put("relme", "realme");
        TYPOS.put("realmi", "realme");
        TYPOS.put("realm", "realme");
        TYPOS.put("vovo", "vivo");
        TYPOS.put("opo", "oppo");
    }

    private record BrandAlias(String key, String canonical, boolean keepTokenInName) {
    }

    private DeviceNameParser() {
    }

    public static ParsedDeviceName parse(String raw) {
        return parse(raw, List.of());
    }

    public static ParsedDeviceName parse(String raw, List<String> shopBrandNames) {
        if (raw == null || raw.isBlank()) {
            return new ParsedDeviceName(null, "", null);
        }
        String cleaned = collapse(correctTypos(raw.trim()));
        String variant = null;
        Matcher variantMatch = VARIANT_SUFFIX.matcher(cleaned);
        if (variantMatch.find()) {
            variant = canonicalizeVariant(variantMatch.group(1));
            cleaned = cleaned.substring(0, variantMatch.start()).trim();
        }

        BrandHit brandHit = matchBrand(cleaned, shopBrandNames);
        String name = brandHit == null ? cleaned : brandHit.remainder();
        if (name.isBlank()) {
            name = cleaned;
        }
        String brand = brandHit == null ? null : brandHit.canonical();
        return new ParsedDeviceName(brand, collapse(name), variant);
    }

    private static String correctTypos(String input) {
        String[] tokens = input.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String replacement = TYPOS.get(tokens[i].toLowerCase(Locale.ROOT));
            if (replacement != null) {
                tokens[i] = replacement;
            }
        }
        return String.join(" ", tokens);
    }

    private static BrandHit matchBrand(String text, List<String> shopBrandNames) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<BrandAlias> candidates = new ArrayList<>();
        if (shopBrandNames != null) {
            for (String shopBrand : shopBrandNames) {
                if (shopBrand == null || shopBrand.isBlank()) {
                    continue;
                }
                candidates.add(new BrandAlias(shopBrand.trim().toLowerCase(Locale.ROOT), shopBrand.trim(), false));
            }
        }
        candidates.addAll(DICTIONARY);
        candidates.sort(Comparator.comparingInt((BrandAlias alias) -> alias.key.length()).reversed());

        for (BrandAlias alias : candidates) {
            if (startsWithWord(lower, alias.key)) {
                String remainder = alias.keepTokenInName
                        ? text
                        : text.substring(alias.key.length()).trim();
                remainder = remainder.replaceFirst("(?i)^[-,/]+\\s*", "").trim();
                return new BrandHit(alias.canonical, remainder);
            }
        }
        return null;
    }

    private static boolean startsWithWord(String lowerText, String lowerKey) {
        if (!lowerText.startsWith(lowerKey)) {
            return false;
        }
        if (lowerText.length() == lowerKey.length()) {
            return true;
        }
        char next = lowerText.charAt(lowerKey.length());
        return next == ' ' || next == '-' || next == '/';
    }

    private static String canonicalizeVariant(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).replace('-', ' ').trim();
        key = collapse(key).replace(" ", "");
        if (key.equals("wifi")) {
            return "WiFi";
        }
        return VARIANT_CANONICAL.getOrDefault(raw.toLowerCase(Locale.ROOT), raw.toUpperCase(Locale.ROOT));
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private record BrandHit(String canonical, String remainder) {
    }
}
