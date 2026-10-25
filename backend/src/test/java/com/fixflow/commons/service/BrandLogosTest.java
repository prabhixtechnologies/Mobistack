package com.fixflow.commons.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandLogosTest {

    private final BrandLogos logos = new BrandLogos();

    @Test
    void packedMarkRidesWithTheBrandAndIgnoresARemoteAddress() {
        String apple = logos.mark("Apple", null);
        assertThat(apple).startsWith("<svg");
        assertThat(apple).doesNotContain("<script");
        assertThat(apple.length()).isLessThanOrEqualTo(BrandLogos.MAX_BYTES);
        assertThat(logos.mark("Apple", "https://example.com/apple.svg")).isEqualTo(apple);
        assertThat(logos.mark("one plus", null)).isEqualTo(logos.mark("OnePlus", null));
        assertThat(logos.mark("Not a brand", "https://cdn.example/logo.png")).isNull();
    }

    @Test
    void aShortStoredSvgReplacesThePackedMark() {
        String custom = "<svg viewBox=\"0 0 24 24\"><path d=\"M0 0h24v24H0z\"/></svg>";
        assertThat(logos.mark("Apple", custom)).isEqualTo(custom);
        assertThat(logos.mark("Apple", "<svg><script>bad</script></svg>")).startsWith("<svg");
        assertThat(BrandLogos.inline("<svg>" + "x".repeat(BrandLogos.MAX_BYTES))).isNull();
    }
}
