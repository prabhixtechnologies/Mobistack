package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.dto.CatalogDtos.CategoryRequest;
import com.fixflow.catalog.dto.CatalogDtos.CategoryResponse;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CatalogMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID shopId, boolean activeOnly) {
        List<Category> categories = activeOnly
                ? categoryRepository.findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopId)
                : categoryRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId);
        return categories.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID shopId, UUID id) {
        return mapper.toResponse(require(shopId, id));
    }

    @Transactional
    public CategoryResponse create(UUID shopId, CategoryRequest request) {
        String code = TextNormalizer.toCode(
                request.code() == null || request.code().isBlank() ? request.name() : request.code());
        if (categoryRepository.existsByShopIdAndCode(shopId, code)) {
            throw ApiException.alreadyExists("A category with code " + code + " already exists.");
        }

        Category category = new Category();
        category.setShopId(shopId);
        category.setCode(code);
        apply(category, request);
        categoryRepository.save(category);

        auditService.record(AuditAction.CATEGORY_CREATED, "Category", category.getId(),
                "Created category \"%s\"".formatted(category.getName()));
        return mapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID shopId, UUID id, CategoryRequest request) {
        Category category = require(shopId, id);
        String previousName = category.getName();
        apply(category, request);
        categoryRepository.save(category);

        auditService.record(AuditAction.CATEGORY_UPDATED, "Category", category.getId(),
                previousName.equals(category.getName())
                        ? "Updated category \"%s\"".formatted(category.getName())
                        : "Renamed category \"%s\" to \"%s\"".formatted(previousName, category.getName()));
        return mapper.toResponse(category);
    }

    /**
     * Categories are deactivated rather than deleted: products already filed
     * under one must keep resolving to a name in history and reports.
     */
    @Transactional
    public void deactivate(UUID shopId, UUID id) {
        Category category = require(shopId, id);
        category.setActive(false);
        categoryRepository.save(category);
        auditService.record(AuditAction.CATEGORY_UPDATED, "Category", id,
                "Deactivated category \"%s\"".formatted(category.getName()));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name());
        category.setIcon(request.icon());
        category.setColor(request.color());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.compatibilityRelevant() != null) {
            category.setCompatibilityRelevant(request.compatibilityRelevant());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    private Category require(UUID shopId, UUID id) {
        return categoryRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Category", id));
    }
}
