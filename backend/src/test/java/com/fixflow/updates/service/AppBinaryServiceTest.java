package com.fixflow.updates.service;

import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AppBinaryServiceTest {

    private AppBinaryService service;

    @BeforeEach
    void setUp() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getUpdates().setAndroidDownloadUrl("https://store.example.test/mobistack/android.apk");
        properties.getUpdates().setIosDownloadUrl("https://store.example.test/mobistack/ios.ipa");
        service = new AppBinaryService(properties);
    }

    @Test
    void catalogUsesStoreUrlsWhenTheStoreIsUnreachable() {
        var catalog = service.catalog();
        assertThat(catalog.android().available()).isFalse();
        assertThat(catalog.ios().available()).isFalse();
        assertThat(catalog.android().url()).isEqualTo("https://store.example.test/mobistack/android.apk");
        assertThat(catalog.ios().url()).isEqualTo("https://store.example.test/mobistack/ios.ipa");
    }

    @Test
    void downloadsRedirectToTheStore() {
        ResponseEntity<Resource> android = service.redirectAndroidToStore();
        assertThat(android.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(android.getHeaders().getLocation())
                .hasToString("https://store.example.test/mobistack/android.apk");
        assertThat(android.getBody()).isNull();

        ResponseEntity<Resource> ios = service.redirectIosToStore();
        assertThat(ios.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(ios.getHeaders().getLocation())
                .hasToString("https://store.example.test/mobistack/ios.ipa");
        assertThat(ios.getBody()).isNull();
    }
}
