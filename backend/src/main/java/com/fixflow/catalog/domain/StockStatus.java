package com.fixflow.catalog.domain;

/**
 * The traffic light shown next to every part.
 *
 * <p>RED means "cannot serve a customer right now" (nothing available) or
 * "about to run out"; ORANGE means at or below the reorder level; GREEN is
 * healthy. A variant with no reorder level set is only ever RED when empty,
 * because there is no threshold to be low against.
 */
public enum StockStatus {

    GREEN,
    ORANGE,
    RED;

    public static StockStatus evaluate(int available, int reorderLevel, double criticalFactor) {
        if (available <= 0) {
            return RED;
        }
        if (reorderLevel <= 0) {
            return GREEN;
        }
        if (available <= Math.max(1, Math.floor(reorderLevel * criticalFactor))) {
            return RED;
        }
        return available <= reorderLevel ? ORANGE : GREEN;
    }
}
