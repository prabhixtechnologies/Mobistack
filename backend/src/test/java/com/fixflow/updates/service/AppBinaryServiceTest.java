package com.fixflow.updates.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppBinaryServiceTest {

    @TempDir
    Path folder;

    private FixFlowProperties properties;
    private AppBinaryService service;

    @BeforeEach
    void setUp() {
        properties = new FixFlowProperties();
        properties.getUpdates().setAndroidApkPath(folder.resolve("MobiStack.apk").toString());
        properties.getUpdates().setIosIpaPath(folder.resolve("MobiStack.ipa").toString());
        properties.getUpdates().setAndroidDownloadUrl("https://store.example.test/mobistack/android.apk");
        service = new AppBinaryService(properties);
    }

    @Test
    void catalogUsesStoreUrlForAndroidEvenWhenLocalFileMissing() {
        var catalog = service.catalog();
        assertThat(catalog.android().available()).isFalse();
        assertThat(catalog.ios().available()).isFalse();
        assertThat(catalog.android().url()).isEqualTo("https://store.example.test/mobistack/android.apk");
        assertThat(catalog.ios().url()).endsWith("/download/ios");
    }

    @Test
    void androidDownloadRedirectsToTheStore() {
        ResponseEntity<Resource> response = service.redirectAndroidToStore();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://store.example.test/mobistack/android.apk");
        assertThat(response.getBody()).isNull();
    }

    @Test
    void missingIpaIsNotFound() {
        assertThatThrownBy(() -> service.serveIos())
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void servesIpaWhenTheFileIsPresent() throws Exception {
        Files.writeString(folder.resolve("MobiStack.ipa"), "ipa-bytes");

        var info = service.info("IOS");
        assertThat(info.available()).isTrue();
        assertThat(info.sizeBytes()).isEqualTo(9);

        ResponseEntity<Resource> response = service.serveIos();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("MobiStack.ipa");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isEqualTo(9);
    }
}
