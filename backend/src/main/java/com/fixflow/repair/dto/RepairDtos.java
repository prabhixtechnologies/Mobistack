package com.fixflow.repair.dto;

import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentResponse;
import com.fixflow.repair.domain.RepairStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RepairDtos {

    private RepairDtos() {
    }

    @Schema(name = "CreateRepairRequest")
    public record CreateRepairRequest(
            UUID customerId,
            UUID deviceModelId,
            UUID technicianUserId,
            @NotBlank String problem,
            String imei,
            BigDecimal estimatedCost,
            BigDecimal laborCharge,
            BigDecimal laborCost,
            String customerNotes,
            String internalNotes,
            Instant expectedAt,
            String idempotencyKey
    ) {
    }

    @Schema(name = "UpdateRepairRequest")
    public record UpdateRepairRequest(
            RepairStatus status,
            UUID technicianUserId,
            BigDecimal laborCharge,
            BigDecimal laborCost,
            String customerNotes,
            String internalNotes,
            Instant expectedAt
    ) {
    }

    @Schema(name = "AddRepairPartRequest")
    public record AddRepairPartRequest(
            UUID variantId,
            @Positive int quantity,
            BigDecimal unitPrice
    ) {
    }

    @Schema(name = "CollectRepairPaymentRequest")
    public record CollectRepairPaymentRequest(@Valid List<PaymentRequest> payments) {
    }

    @Schema(name = "RepairPartResponse")
    public record RepairPartResponse(
            UUID id,
            UUID variantId,
            String variantName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            BigDecimal lineTotal
    ) {
    }

    @Schema(name = "RepairResponse")
    public record RepairResponse(
            UUID id,
            String jobNumber,
            RepairStatus status,
            UUID customerId,
            String customerName,
            UUID deviceModelId,
            String deviceName,
            UUID technicianUserId,
            String problem,
            String imei,
            BigDecimal estimatedCost,
            BigDecimal laborCharge,
            BigDecimal laborCost,
            BigDecimal partsTotal,
            BigDecimal partsCost,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal outstanding,
            BigDecimal profit,
            String customerNotes,
            String internalNotes,
            Instant expectedAt,
            Instant deliveredAt,
            Instant createdAt,
            List<RepairPartResponse> parts,
            List<PaymentResponse> payments
    ) {
    }
}
