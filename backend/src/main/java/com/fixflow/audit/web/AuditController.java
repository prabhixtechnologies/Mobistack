package com.fixflow.audit.web;

import com.fixflow.audit.domain.AuditLog;
import com.fixflow.audit.repository.AuditLogRepository;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize(Authorize.AUDIT_READ)
    public PageResponse<AuditLog> list(@PageableDefault(size = 40) Pageable pageable) {
        return PageResponse.of(auditLogRepository.findByShopIdOrderByCreatedAtDesc(CurrentUser.shopId(), pageable));
    }
}
