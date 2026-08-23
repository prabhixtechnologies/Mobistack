package com.fixflow.brand.web;

import com.fixflow.config.FixFlowProperties;
import com.fixflow.updates.service.AppBinaryService;
import com.fixflow.updates.service.AppReleaseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicBrandController {

    private final FixFlowProperties properties;
    private final AppReleaseService appReleaseService;
    private final AppBinaryService appBinaryService;

    @GetMapping("/brand")
    @SecurityRequirements
    public Map<String, Object> brand() {
        return platform();
    }

    @GetMapping("/platform")
    @SecurityRequirements
    public Map<String, Object> platform() {
        var brand = properties.getBrand();
        var platform = properties.getPlatform();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organization", brand.getOrganization());
        body.put("product", brand.getProduct());
        body.put("tagline", brand.getTagline());
        body.put("organizationTagline", brand.getOrganizationTagline());
        body.put("copyrightYear", brand.getCopyrightYear());
        body.put("copyright", "© " + brand.getCopyrightYear() + " " + brand.getOrganization() + ". All rights reserved.");
        body.put("publicOrigin", platform.getPublicOrigin());
        body.put("apiOrigin", platform.getApiOrigin());
        body.put("webOrigin", properties.getAuth().getWebOrigin());
        body.put("supportEmail", platform.getSupportEmail());
        body.put("supportPhone", platform.getSupportPhone());
        body.put("httpsRequired", platform.isRequireHttps());
        var downloads = appBinaryService.catalog();
        body.put("androidDownloadUrl", downloads.android().url());
        body.put("iosDownloadUrl", downloads.ios().url());
        body.put("androidDownloadAvailable", downloads.android().available());
        body.put("iosDownloadAvailable", downloads.ios().available());
        return body;
    }

    @GetMapping("/app-release")
    @SecurityRequirements
    public AppReleaseService.ReleasePolicy appRelease(
            @RequestParam(defaultValue = "WEB") String platform,
            @RequestParam(required = false) Integer build) {
        return appReleaseService.policy(platform, build);
    }
}
