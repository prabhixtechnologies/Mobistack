package com.fixflow.imports.web;

import com.fixflow.common.web.PageResponse;
import com.fixflow.imports.ImportService;
import com.fixflow.imports.ImportService.ImportRequest;
import com.fixflow.imports.domain.ImportJob;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
@Tag(name = "Imports")
public class ImportController {

    private final ImportService importService;

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    public PageResponse<ImportJob> jobs(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(importService.jobs(CurrentUser.shopId(), pageable));
    }

    @PostMapping("/compatibility")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public Map<String, Object> compatibility(@RequestBody ImportRequest request) {
        return importService.asMap(importService.importCompatibility(CurrentUser.shopId(), request));
    }
}
