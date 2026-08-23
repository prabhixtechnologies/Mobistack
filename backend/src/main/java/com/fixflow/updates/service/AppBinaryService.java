package com.fixflow.updates.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class AppBinaryService {

    public static final String ANDROID_FILENAME = "MobiStack.apk";
    public static final String IOS_FILENAME = "MobiStack.ipa";
    public static final String ANDROID_CONTENT_TYPE = "application/vnd.android.package-archive";
    public static final String IOS_CONTENT_TYPE = "application/octet-stream";

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
        Path path = resolve(ios);
        if (!Files.isRegularFile(path)) {
            return new PackageInfo(ios ? "IOS" : "ANDROID", false, filename, 0L, url, contentType);
        }
        try {
            return new PackageInfo(ios ? "IOS" : "ANDROID", true, filename, Files.size(path), url, contentType);
        } catch (IOException ex) {
            return new PackageInfo(ios ? "IOS" : "ANDROID", false, filename, 0L, url, contentType);
        }
    }

    public ResponseEntity<Resource> serve(String platform) {
        boolean ios = isIos(platform);
        PackageInfo pkg = info(platform);
        if (!pkg.available()) {
            throw new ApiException(ErrorCode.NOT_FOUND, ios
                    ? "The iOS package is not on the server yet. Open /app/ios or email support."
                    : "The Android package is not on the server yet. Open /app/android or email support.");
        }
        FileSystemResource resource = new FileSystemResource(resolve(ios));
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

    private String publicUrl(boolean ios) {
        String origin = properties.getPlatform().getPublicOrigin();
        if (origin == null || origin.isBlank()) {
            origin = "";
        } else {
            origin = origin.replaceAll("/+$", "");
        }
        return origin + (ios ? "/download/ios" : "/download/android");
    }

    private static boolean isIos(String platform) {
        return platform != null && "IOS".equalsIgnoreCase(platform.trim());
    }
}
