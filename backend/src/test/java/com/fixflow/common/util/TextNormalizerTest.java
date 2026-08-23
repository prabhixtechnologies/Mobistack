package com.fixflow.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void collapsesCasePunctuationAndSpacing() {
        assertThat(TextNormalizer.normalize("Realme  6i")).isEqualTo("realme 6i");
        assertThat(TextNormalizer.normalize("REALME-6I")).isEqualTo("realme 6i");
        assertThat(TextNormalizer.normalize("  Realme_6i  ")).isEqualTo("realme 6i");
    }

    @Test
    void stripsAccents() {
        assertThat(TextNormalizer.normalize("Réalmé 6")).isEqualTo("realme 6");
    }

    @Test
    void blankBecomesEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
        assertThat(TextNormalizer.normalizeOrNull("   ")).isNull();
    }

    @Test
    void skuAndCodeHelpers() {
        assertThat(TextNormalizer.normalizeSku(" dis-11 ")).isEqualTo("DIS-11");
        assertThat(TextNormalizer.toCode("Realme 6 display")).isEqualTo("REALME_6_DISPLAY");
    }
}
