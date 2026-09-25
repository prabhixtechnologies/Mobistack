package com.fixflow.repair.web;

import com.fixflow.common.web.PageResponse;
import com.fixflow.repair.domain.RepairStatus;
import com.fixflow.repair.dto.RepairDtos.AddRepairPartRequest;
import com.fixflow.repair.dto.RepairDtos.CollectRepairPaymentRequest;
import com.fixflow.repair.dto.RepairDtos.CreateRepairRequest;
import com.fixflow.repair.dto.RepairDtos.RepairResponse;
import com.fixflow.repair.dto.RepairDtos.UpdateRepairRequest;
import com.fixflow.repair.service.RepairService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/repairs")
@RequiredArgsConstructor
@Tag(name = "Repairs")
public class RepairController {

    private final RepairService repairService;

    @GetMapping
    @PreAuthorize(Authorize.REPAIR_READ)
    public PageResponse<RepairResponse> list(@RequestParam(required = false) RepairStatus status,
                                             @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(repairService.list(CurrentUser.shopId(), status, pageable));
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.REPAIR_READ)
    public RepairResponse get(@RequestParam UUID id) {
        return repairService.get(CurrentUser.shopId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.REPAIR_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public RepairResponse create(@Valid @RequestBody CreateRepairRequest request) {
        return repairService.create(CurrentUser.shopId(), request);
    }

    @PutMapping
    @PreAuthorize(Authorize.REPAIR_WRITE)
    public RepairResponse update(@RequestParam UUID id, @Valid @RequestBody UpdateRepairRequest request) {
        return repairService.update(CurrentUser.shopId(), id, request);
    }

    @PostMapping("/parts")
    @PreAuthorize(Authorize.REPAIR_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public RepairResponse addPart(@RequestParam UUID id, @Valid @RequestBody AddRepairPartRequest request) {
        return repairService.addPart(CurrentUser.shopId(), id, request);
    }

    @PostMapping("/payments")
    @PreAuthorize(Authorize.REPAIR_WRITE)
    public RepairResponse collect(@RequestParam UUID id, @Valid @RequestBody CollectRepairPaymentRequest request) {
        return repairService.collect(CurrentUser.shopId(), id, request);
    }
}
