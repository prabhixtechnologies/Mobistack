package com.fixflow.updates.web;

import com.fixflow.updates.service.AppBinaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Public")
public class AppDownloadController {

    private final AppBinaryService binaries;

    @GetMapping("/api/v1/public/downloads")
    @SecurityRequirements
    public AppBinaryService.Catalog catalog() {
        return binaries.catalog();
    }

    /**
     * Legacy MobiStack download paths. Android packages are hosted only on the company store
     * (S3-backed); these URLs permanently redirect there and never stream bytes.
     */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD}, path = {
            "/download/android",
            "/download/android.apk",
            "/api/v1/public/downloads/android"
    })
    @SecurityRequirements
    public ResponseEntity<Resource> android() {
        return binaries.redirectAndroidToStore();
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD}, path = {
            "/download/ios",
            "/download/ios.ipa",
            "/api/v1/public/downloads/ios"
    })
    @SecurityRequirements
    public ResponseEntity<Resource> ios() {
        return binaries.serveIos();
    }
}
