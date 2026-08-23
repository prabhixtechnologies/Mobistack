package com.fixflow.catalog.web;

import com.fixflow.catalog.dto.CatalogDtos.CategoryRequest;
import com.fixflow.catalog.dto.CatalogDtos.CategoryResponse;
import com.fixflow.catalog.service.CategoryService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    public List<CategoryResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return categoryService.list(CurrentUser.shopId(), activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.CATALOG_READ)
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(CurrentUser.shopId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(CurrentUser.shopId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(CurrentUser.shopId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        categoryService.deactivate(CurrentUser.shopId(), id);
    }
}
