package com.fixflow.catalog;

/**
 * How a handset is written on the universal list: {@code Samsung A32 4G}.
 */
public final class DeviceLabels {

    private DeviceLabels() {
    }

    public static String display(String brandName, String name, String variant) {
        StringBuilder label = new StringBuilder();
        append(label, brandName);
        append(label, name);
        append(label, variant);
        return label.toString();
    }

    public static String modelLabel(String name, String variant) {
        if (variant == null || variant.isBlank()) {
            return name == null ? "" : name.trim();
        }
        if (name == null || name.isBlank()) {
            return variant.trim();
        }
        return name.trim() + " " + variant.trim();
    }

    private static void append(StringBuilder label, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!label.isEmpty()) {
            label.append(' ');
        }
        label.append(part.trim());
    }
}
