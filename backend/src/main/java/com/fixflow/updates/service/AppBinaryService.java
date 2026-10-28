package com.fixflow.updates.service;

import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AppBinaryService {

    public static final String ANDROID_FILENAME = "MobiStack.apk";
    public static final String IOS_FILENAME = "MobiStack.ipa";
    public static final String ANDROID_CONTENT_TYPE = "application/vnd.android.package-archive";
    public static final String IOS_CONTENT_TYPE = "application/octet-stream";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record PackageInfo(
            String platform,
            boolean available,
            String filename,
            long sizeBytes,
            String url,
            String contentType
    ) {
    }

    public record Catalog(PackageInfo android, PackageInfo ios) {
    }

    private final FixFlowProperties properties;

    public Catalog catalog() {
        return new Catalog(info("ANDROID"), info("IOS"));
    }

    public PackageInfo info(String platform) {
        boolean ios = isIos(platform);
        String filename = ios ? IOS_FILENAME : ANDROID_FILENAME;
        String contentType = ios ? IOS_CONTENT_TYPE : ANDROID_CONTENT_TYPE;
        return probeRemote(publicUrl(ios), filename, contentType, ios ? "IOS" : "ANDROID");
    }

    /**
     * Packages live on the company store. These paths permanently redirect there and never stream bytes.
     */
    public ResponseEntity<Resource> redirectAndroidToStore() {
        return redirectToStore(false);
    }

    public ResponseEntity<Resource> redirectIosToStore() {
        return redirectToStore(true);
    }

    private ResponseEntity<Resource> redirectToStore(boolean ios) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(publicUrl(ios)))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .build();
    }

    private PackageInfo probeRemote(String url, String filename, String contentType, String platform) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(4))
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                long size = response.headers().firstValueAsLong("content-length").orElse(0L);
                return new PackageInfo(platform, true, filename, size, url, contentType);
            }
        } catch (Exception ignored) {
            // Catalog must still answer when the store is briefly unreachable.
        }
        return new PackageInfo(platform, false, filename, 0L, url, contentType);
    }

    private String publicUrl(boolean ios) {
        String configured = ios
                ? properties.getUpdates().getIosDownloadUrl()
                : properties.getUpdates().getAndroidDownloadUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return ios
                ? "https://store.prabhixtechnologies.com/mobistack/ios.ipa"
                : "https://store.prabhixtechnologies.com/mobistack/android.apk";
    }

    private static boolean isIos(String platform) {
        return platform != null && "IOS".equalsIgnoreCase(platform.trim());
    }
}
