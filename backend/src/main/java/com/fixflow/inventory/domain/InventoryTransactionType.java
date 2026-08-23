package com.fixflow.inventory.domain;

/**
 * Every stock movement is one of these. {@code onHandDelta} and
 * {@code reservedDelta} encode the arithmetic so the ledger stays additive:
 * replaying it from zero always reproduces current stock.
 */
public enum InventoryTransactionType {

    /** Goods received from a supplier or added manually. */
    IN(1, 0),
    /** Sold, or consumed by a repair job. */
    OUT(-1, 0),
    /** Customer brought a part back. */
    RETURN(1, 0),
    /** Broken, lost or written off. */
    DAMAGE(-1, 0),
    /** Stock-take correction; direction is decided per transaction. */
    ADJUSTMENT(0, 0),
    /** Held for an open repair job: still on hand, no longer available. */
    RESERVATION(0, 1),
    /** Reservation cancelled. */
    RELEASE(0, -1),
    /** Balance carried in when the shop starts using MobiStack. */
    OPENING(1, 0),
    /** Moved to another location or counter. */
    TRANSFER(0, 0);

    private final int onHandSign;
    private final int reservedSign;

    InventoryTransactionType(int onHandSign, int reservedSign) {
        this.onHandSign = onHandSign;
        this.reservedSign = reservedSign;
    }

    public int onHandDelta(int quantity) {
        return onHandSign * quantity;
    }

    public int reservedDelta(int quantity) {
        return reservedSign * quantity;
    }

    /** ADJUSTMENT and TRANSFER carry an explicit signed delta from the caller. */
    public boolean hasFixedDirection() {
        return this != ADJUSTMENT && this != TRANSFER;
    }

    public boolean increasesStock() {
        return onHandSign > 0;
    }
}
