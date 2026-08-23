package com.fixflow.catalog.domain;

import java.util.List;

/**
 * The starting category set every new shop gets. This is a template, not a
 * constraint: once copied into the shop's own rows they are fully editable.
 */
public final class DefaultCategories {

    public record Template(String code, String name, String icon, String color, int sortOrder,
                           boolean compatibilityRelevant) {
    }

    public static final List<Template> ALL = List.of(
            new Template("TEMPERED_GLASS", "Tempered Glass", "shield", "#38BDF8", 10, true),
            new Template("TOUCH_OCA", "Touch / OCA", "hand-pointer", "#A78BFA", 20, true),
            new Template("DISPLAY_FOLDER", "Folder / Display / Combo", "smartphone", "#F472B6", 30, true),
            new Template("DISPLAY_CONNECTOR", "Display Connector", "plug", "#FB923C", 40, true),
            new Template("FRAME", "Frame / Middle Frame", "square", "#94A3B8", 50, true),
            new Template("BACK_COVER", "Back Cover", "layers", "#34D399", 60, true),
            new Template("BATTERY", "Battery", "battery", "#22C55E", 70, true),
            new Template("POWER_VOLUME_FLEX", "Power / Volume Flex", "toggle-left", "#EAB308", 80, true),
            new Template("CHARGING_BOARD", "Charging Board", "zap", "#F59E0B", 90, true),
            new Template("CAMERA", "Camera", "camera", "#60A5FA", 100, true),
            new Template("SPEAKER", "Speaker", "volume-2", "#C084FC", 110, true),
            new Template("MICROPHONE", "Microphone", "mic", "#2DD4BF", 120, true),
            new Template("IC_CHIP", "IC / Chip", "cpu", "#F87171", 130, true),
            new Template("OTHER", "Other", "package", "#64748B", 200, false)
    );

    private DefaultCategories() {
    }
}
