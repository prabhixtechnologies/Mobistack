package com.fixflow.imports;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupResponse;
import com.fixflow.catalog.service.CompatibilityGroupService;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.common.error.ApiException;
import com.fixflow.imports.ImportService.ImportRequest;
import com.fixflow.imports.repository.ImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private CompatibilityGroupService compatibilityGroupService;
    @Mock
    private AuditService auditService;
    @Mock
    private ImportJobRepository importJobRepository;

    private ImportService importService;
    private UUID shopId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        importService = new ImportService(deviceService, compatibilityGroupService, auditService,
                importJobRepository, new ObjectMapper());
        shopId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
    }

    @Test
    void eachEqualsLineBecomesARealGroupNotAliases() {
        DeviceModel a32 = device("A32", "4G");
        DeviceModel m32 = device("M32", "4G");
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Samsung A32 4G"), any())).thenReturn(a32);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Samsung M32 4G"), any())).thenReturn(m32);
        when(compatibilityGroupService.create(eq(shopId), any())).thenReturn(dummyGroup());
        when(importJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = importService.importCompatibility(shopId, new ImportRequest(
                "Samsung",
                "Samsung A32 4G = Samsung M32 4G\n# skip me",
                "paste",
                categoryId));

        assertThat(result.groups()).isEqualTo(1);
        assertThat(result.devices()).isEqualTo(2);
        assertThat(result.aliases()).isZero();
        ArgumentCaptor<CompatibilityGroupRequest> captor = ArgumentCaptor.forClass(CompatibilityGroupRequest.class);
        verify(compatibilityGroupService).create(eq(shopId), captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(categoryId);
        assertThat(captor.getValue().deviceModelIds()).containsExactly(a32.getId(), m32.getId());
        assertThat(captor.getValue().deviceTexts()).isNull();
    }

    @Test
    void numberedPasteLinesStillImport() {
        DeviceModel nine = device("9", null);
        DeviceModel nineA = device("9A", null);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Redmi 9"), any())).thenReturn(nine);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Redmi 9A"), any())).thenReturn(nineA);
        when(compatibilityGroupService.create(eq(shopId), any())).thenReturn(dummyGroup());
        when(importJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        importService.importCompatibility(shopId, new ImportRequest(
                null, "3. Redmi 9 = Redmi 9A ✅", "paste", categoryId));

        verify(compatibilityGroupService, times(1)).create(eq(shopId), any());
    }

    @Test
    void requiresACategory() {
        assertThatThrownBy(() -> importService.importCompatibility(shopId,
                new ImportRequest("Samsung", "A = B", "paste", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("category");
    }

    private DeviceModel device(String name, String variant) {
        Brand brand = new Brand();
        brand.setName("Samsung");
        DeviceModel device = new DeviceModel();
        device.setId(UUID.randomUUID());
        device.setBrand(brand);
        device.setName(name);
        device.setVariant(variant);
        return device;
    }

    private CompatibilityGroupResponse dummyGroup() {
        return new CompatibilityGroupResponse(UUID.randomUUID(), "CODE", "Name", categoryId, "Glass",
                null, false, true, List.of(), 0, null);
    }
}
