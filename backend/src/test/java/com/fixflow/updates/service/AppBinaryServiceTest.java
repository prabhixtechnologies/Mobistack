package com.fixflow.updates.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
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
        service = new AppBinaryService(properties);
    }

    @Test
    void catalogMarksMissingPackagesUnavailable() {
        var catalog = service.catalog();
        assertThat(catalog.android().available()).isFalse();
        assertThat(catalog.ios().available()).isFalse();
        assertThat(catalog.android().url()).endsWith("/download/android");
        assertThat(catalog.ios().url()).endsWith("/download/ios");
    }

    @Test
    void servesTheApkWhenTheFileIsPresent() throws Exception {
        Files.writeString(folder.resolve("MobiStack.apk"), "apk-bytes");

        var info = service.info("ANDROID");
        assertThat(info.available()).isTrue();
        assertThat(info.sizeBytes()).isEqualTo(9);

        ResponseEntity<Resource> response = service.serve("ANDROID");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("MobiStack.apk");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isEqualTo(9);
    }

    @Test
    void missingIpaIsNotFound() {
        assertThatThrownBy(() -> service.serve("IOS"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void directoryPathResolvesTheDefaultFilename() throws Exception {
        properties.getUpdates().setAndroidApkPath(folder.toString());
        Files.writeString(folder.resolve("MobiStack.apk"), "from-dir");

        assertThat(service.info("ANDROID").available()).isTrue();
        assertThat(service.info("ANDROID").sizeBytes()).isEqualTo(8);
    }
}
