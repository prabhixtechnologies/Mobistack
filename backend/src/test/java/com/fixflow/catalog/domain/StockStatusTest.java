package com.fixflow.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockStatusTest {

    @Test
    void emptyIsAlwaysRed() {
        assertThat(StockStatus.evaluate(0, 5, 0.34)).isEqualTo(StockStatus.RED);
        assertThat(StockStatus.evaluate(-1, 0, 0.34)).isEqualTo(StockStatus.RED);
    }

    @Test
    void noReorderLevelIsGreenWhenAnythingIsOnTheShelf() {
        assertThat(StockStatus.evaluate(1, 0, 0.34)).isEqualTo(StockStatus.GREEN);
    }

    @Test
    void atReorderLevelIsOrange() {
        assertThat(StockStatus.evaluate(3, 3, 0.34)).isEqualTo(StockStatus.ORANGE);
        assertThat(StockStatus.evaluate(4, 3, 0.34)).isEqualTo(StockStatus.GREEN);
    }

    @Test
    void wellBelowReorderLevelIsRed() {
        // 0.34 * 10 = 3.4 → floor 3, so 3 or fewer is critical.
        assertThat(StockStatus.evaluate(3, 10, 0.34)).isEqualTo(StockStatus.RED);
        assertThat(StockStatus.evaluate(4, 10, 0.34)).isEqualTo(StockStatus.ORANGE);
    }
}
