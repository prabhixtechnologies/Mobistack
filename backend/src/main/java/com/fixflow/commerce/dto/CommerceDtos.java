package com.fixflow.commerce.dto;

import com.fixflow.commerce.domain.PaymentMethod;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.commerce.domain.PurchaseStatus;
import com.fixflow.commerce.domain.SaleStatus;
import com.fixflow.pricing.domain.PricingFlag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommerceDtos {

    private CommerceDtos() {
    }

    @Schema(name = "SaleLineRequest")
    public record SaleLineRequest(
            UUID variantId,
            @Positive int quantity,
            BigDecimal unitPrice,
            BigDecimal discount
    ) {
    }

    @Schema(name = "PaymentRequest")
    public record PaymentRequest(
            PaymentMethod method,
            @Positive BigDecimal amount,
            String notes
    ) {
    }

    @Schema(name = "CreateSaleRequest")
    public record CreateSaleRequest(
            UUID customerId,
            PricingFlag pricingFlag,
            BigDecimal discount,
            String notes,
            String idempotencyKey,
            String deviceId,
            @NotEmpty @Valid List<SaleLineRequest> items,
            @Valid List<PaymentRequest> payments
    ) {
    }

    @Schema(name = "VoidSaleRequest")
    public record VoidSaleRequest(String reason) {
    }

    @Schema(name = "SaleItemResponse")
    public record SaleItemResponse(
            UUID id,
            UUID variantId,
            String variantName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            BigDecimal discount,
            BigDecimal lineTotal,
            BigDecimal profit
    ) {
    }

    @Schema(name = "PaymentResponse")
    public record PaymentResponse(
            UUID id,
            PaymentMethod method,
            BigDecimal amount,
            PaymentStatus status,
            Instant occurredAt,
            String notes
    ) {
    }

    @Schema(name = "SaleResponse")
    public record SaleResponse(
            UUID id,
            String invoiceNumber,
            SaleStatus status,
            UUID customerId,
            String customerName,
            PricingFlag pricingFlag,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal outstanding,
            BigDecimal profit,
            String notes,
            Instant occurredAt,
            List<SaleItemResponse> items,
            List<PaymentResponse> payments
    ) {
    }

    @Schema(name = "PurchaseLineRequest")
    public record PurchaseLineRequest(
            UUID variantId,
            @Positive int quantity,
            BigDecimal unitCost,
            String batchNo
    ) {
    }

    @Schema(name = "CreatePurchaseRequest")
    public record CreatePurchaseRequest(
            UUID supplierId,
            BigDecimal tax,
            String notes,
            String idempotencyKey,
            @NotEmpty @Valid List<PurchaseLineRequest> items,
            @Valid List<PaymentRequest> payments
    ) {
    }

    @Schema(name = "PurchaseItemResponse")
    public record PurchaseItemResponse(
            UUID id,
            UUID variantId,
            String variantName,
            int quantity,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            String batchNo
    ) {
    }

    @Schema(name = "PurchaseResponse")
    public record PurchaseResponse(
            UUID id,
            UUID supplierId,
            String supplierName,
            PurchaseStatus status,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal outstanding,
            String notes,
            Instant receivedAt,
            List<PurchaseItemResponse> items,
            List<PaymentResponse> payments
    ) {
    }
}
