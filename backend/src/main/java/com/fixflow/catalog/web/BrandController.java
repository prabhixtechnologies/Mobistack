package com.fixflow.catalog.web;

import com.fixflow.catalog.dto.CatalogDtos.BrandRequest;
import com.fixflow.catalog.dto.CatalogDtos.BrandResponse;
import com.fixflow.catalog.service.BrandService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/brands")
@RequiredArgsConstructor
@Tag(name = "Brands")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    public List<BrandResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return brandService.list(CurrentUser.shopId(), activeOnly);
    }

    @PostMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse create(@Valid @RequestBody BrandRequest request) {
        return brandService.create(CurrentUser.shopId(), request);
    }

    @PutMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public BrandResponse update(@RequestParam UUID id, @Valid @RequestBody BrandRequest request) {
        return brandService.update(CurrentUser.shopId(), id, request);
    }
}
