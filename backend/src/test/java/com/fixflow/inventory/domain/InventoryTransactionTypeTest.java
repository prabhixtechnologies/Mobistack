package com.fixflow.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTransactionTypeTest {

    @Test
    void inAndOutAreOpposites() {
        assertThat(InventoryTransactionType.IN.onHandDelta(5)).isEqualTo(5);
        assertThat(InventoryTransactionType.OUT.onHandDelta(5)).isEqualTo(-5);
        assertThat(InventoryTransactionType.DAMAGE.onHandDelta(2)).isEqualTo(-2);
        assertThat(InventoryTransactionType.RETURN.onHandDelta(2)).isEqualTo(2);
    }

    @Test
    void reservationMovesAvailabilityNotOnHand() {
        assertThat(InventoryTransactionType.RESERVATION.onHandDelta(3)).isZero();
        assertThat(InventoryTransactionType.RESERVATION.reservedDelta(3)).isEqualTo(3);
        assertThat(InventoryTransactionType.RELEASE.reservedDelta(3)).isEqualTo(-3);
    }

    @Test
    void adjustmentHasNoInherentDirection() {
        assertThat(InventoryTransactionType.ADJUSTMENT.hasFixedDirection()).isFalse();
        assertThat(InventoryTransactionType.IN.hasFixedDirection()).isTrue();
    }
}
