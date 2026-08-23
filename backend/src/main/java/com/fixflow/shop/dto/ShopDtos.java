package com.fixflow.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public final class ShopDtos {

    private ShopDtos() {
    }

    @Schema(name = "ShopResponse")
    public record ShopResponse(
            UUID id,
            String name,
            String legalName,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String gstNumber,
            String currencyCode,
            String timezone,
            String invoicePrefix,
            String joinCode,
            boolean requireCompatibilityApproval,
            int maxDevicesPerUser,
            Map<String, Object> settings
    ) {
    }

    @Schema(name = "UpdateShopRequest")
    public record UpdateShopRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 200) String legalName,
            @Size(max = 32) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 120) String city,
            @Size(max = 120) String state,
            @Size(max = 20) String postalCode,
            @Size(max = 80) String country,
            @Size(max = 20) String gstNumber,
            @Size(max = 3) String currencyCode,
            @Size(max = 64) String timezone,
            @Size(max = 12) String invoicePrefix,
            Boolean requireCompatibilityApproval,
            Integer maxDevicesPerUser,
            Map<String, Object> settings
    ) {
    }
}
