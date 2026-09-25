package com.fixflow.catalog.web;

import com.fixflow.catalog.domain.CompatibilityChangeRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupResponse;
import com.fixflow.catalog.service.CompatibilityGroupService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/compatibility-requests")
@RequiredArgsConstructor
@Tag(name = "Compatibility requests")
public class CompatibilityRequestController {

    private final CompatibilityGroupService compatibilityGroupService;

    @GetMapping
    @PreAuthorize(Authorize.COMPATIBILITY_APPROVE)
    public List<CompatibilityChangeRequest> pending() {
        return compatibilityGroupService.pendingRequests(CurrentUser.shopId());
    }

    @PostMapping("/approve")
    @PreAuthorize(Authorize.COMPATIBILITY_APPROVE)
    public CompatibilityGroupResponse approve(@RequestParam UUID id) {
        return compatibilityGroupService.decide(CurrentUser.shopId(), id, true);
    }

    @PostMapping("/reject")
    @PreAuthorize(Authorize.COMPATIBILITY_APPROVE)
    public CompatibilityGroupResponse reject(@RequestParam UUID id) {
        return compatibilityGroupService.decide(CurrentUser.shopId(), id, false);
    }
}
