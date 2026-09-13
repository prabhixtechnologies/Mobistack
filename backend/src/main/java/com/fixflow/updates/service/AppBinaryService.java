package com.fixflow.updates.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String url = publicUrl(ios);
        if (!ios) {
            return probeRemote(url, filename, contentType, "ANDROID");
        }
        Path path = resolve(true);
        if (!Files.isRegularFile(path)) {
            return new PackageInfo("IOS", false, filename, 0L, url, contentType);
        }
        try {
            return new PackageInfo("IOS", true, filename, Files.size(path), url, contentType);
        } catch (IOException ex) {
            return new PackageInfo("IOS", false, filename, 0L, url, contentType);
        }
    }

    /**
     * Android packages are not served from this host — they live on the company store (S3).
     * Callers hitting /download/android get a permanent redirect to that store URL.
     */
    public ResponseEntity<Resource> redirectAndroidToStore() {
        String url = publicUrl(false);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(url))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .build();
    }

    public ResponseEntity<Resource> serveIos() {
        PackageInfo pkg = info("IOS");
        if (!pkg.available()) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "The iOS package is not on the server yet. Open /app/ios or email support.");
        }
        FileSystemResource resource = new FileSystemResource(resolve(true));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pkg.contentType()))
                .contentLength(pkg.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pkg.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(resource);
    }

    Path resolve(boolean ios) {
        String configured = ios
                ? properties.getUpdates().getIosIpaPath()
                : properties.getUpdates().getAndroidApkPath();
        Path path = Path.of(configured == null || configured.isBlank()
                ? (ios ? "/var/mobistack/downloads/" + IOS_FILENAME : "/var/mobistack/downloads/" + ANDROID_FILENAME)
                : configured);
        if (Files.isDirectory(path)) {
            path = path.resolve(ios ? IOS_FILENAME : ANDROID_FILENAME);
        }
        return path.toAbsolutePath().normalize();
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
        if (ios) {
            String configured = properties.getUpdates().getIosDownloadUrl();
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
            String origin = properties.getPlatform().getPublicOrigin();
            if (origin == null || origin.isBlank()) {
                origin = "";
            } else {
                origin = origin.replaceAll("/+$", "");
            }
            return origin + "/download/ios";
        }
        String configured = properties.getUpdates().getAndroidDownloadUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "https://store.prabhixtechnologies.com/mobistack/android.apk";
    }

    private static boolean isIos(String platform) {
        return platform != null && "IOS".equalsIgnoreCase(platform.trim());
    }
}
