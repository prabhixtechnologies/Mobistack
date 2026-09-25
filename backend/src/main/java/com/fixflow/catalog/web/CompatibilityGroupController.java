package com.fixflow.catalog.web;

import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupResponse;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityOverviewResponse;
import com.fixflow.catalog.dto.CatalogDtos.CopyGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupDeviceRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupMembershipRequest;
import com.fixflow.catalog.service.CompatibilityGroupService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/compatibility-groups")
@RequiredArgsConstructor
@Tag(name = "Compatibility groups")
public class CompatibilityGroupController {

    private final CompatibilityGroupService compatibilityGroupService;

    @GetMapping("/overview")
    @PreAuthorize(Authorize.CATALOG_READ)
    public CompatibilityOverviewResponse overview() {
        return compatibilityGroupService.overview(CurrentUser.shopId());
    }

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    public PageResponse<CompatibilityGroupResponse> list(@RequestParam(required = false) UUID categoryId,
                                                         @RequestParam(required = false) String q,
                                                         @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(compatibilityGroupService.list(CurrentUser.shopId(), categoryId, q, pageable));
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.CATALOG_READ)
    public CompatibilityGroupResponse get(@RequestParam UUID id) {
        return compatibilityGroupService.get(CurrentUser.shopId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CompatibilityGroupResponse create(@Valid @RequestBody CompatibilityGroupRequest request) {
        return compatibilityGroupService.create(CurrentUser.shopId(), request);
    }

    @PostMapping("/copy")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CompatibilityGroupResponse copy(@RequestParam UUID id,
                                           @RequestBody(required = false) CopyGroupRequest request) {
        return compatibilityGroupService.copy(CurrentUser.shopId(), id, request);
    }

    @PutMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public CompatibilityGroupResponse update(@RequestParam UUID id,
                                             @Valid @RequestBody CompatibilityGroupRequest request) {
        return compatibilityGroupService.update(CurrentUser.shopId(), id, request);
    }

    @PutMapping("/membership")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public CompatibilityGroupResponse replaceMembership(@RequestParam UUID id,
                                                        @RequestBody GroupMembershipRequest request) {
        return compatibilityGroupService.replaceMembership(CurrentUser.shopId(), id, request);
    }

    @PostMapping("/devices")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public CompatibilityGroupResponse addDevice(@RequestParam UUID id,
                                                @Valid @RequestBody GroupDeviceRequest request) {
        return compatibilityGroupService.addDevice(CurrentUser.shopId(), id, request);
    }

    @DeleteMapping("/devices")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    public CompatibilityGroupResponse removeDevice(@RequestParam UUID id, @RequestParam UUID deviceId) {
        return compatibilityGroupService.removeDevice(CurrentUser.shopId(), id, deviceId);
    }

    @DeleteMapping
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam UUID id) {
        compatibilityGroupService.delete(CurrentUser.shopId(), id);
    }

    @GetMapping("/history")
    @PreAuthorize(Authorize.CATALOG_READ)
    public java.util.List<com.fixflow.catalog.domain.CompatibilityHistory> history(@RequestParam UUID id) {
        return compatibilityGroupService.history(CurrentUser.shopId(), id);
    }
}
