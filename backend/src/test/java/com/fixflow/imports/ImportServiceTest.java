package com.fixflow.imports;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.common.error.ApiException;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.service.CatalogCache;
import com.fixflow.commons.service.CatalogFamilyService;
import com.fixflow.commons.service.CommonsCatalogService;
import com.fixflow.imports.ImportService.ImportRequest;
import com.fixflow.imports.repository.ImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
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
    private DeviceModelRepository deviceModels;
    @Mock
    private CategoryRepository categories;
    @Mock
    private CommonsCatalogService catalog;
    @Mock
    private CatalogFamilyService families;
    @Mock
    private CatalogCache cache;
    @Mock
    private AuditService auditService;
    @Mock
    private ImportJobRepository importJobRepository;

    private ImportService importService;
    private UUID shopId;
    private UUID actorId;
    private UUID groupId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        importService = new ImportService(deviceService, deviceModels, categories, catalog, families, cache,
                auditService, importJobRepository, new ObjectMapper());
        shopId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
    }

    @Test
    void eachEqualsLineBecomesACatalogFamily() {
        Category category = new Category();
        category.setCode("TEMPERED_GLASS");
        when(categories.findByIdAndShopId(categoryId, shopId)).thenReturn(Optional.of(category));
        DeviceModel a32 = device("A32", "4G");
        DeviceModel m32 = device("M32", "4G");
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Samsung A32 4G"), any())).thenReturn(a32);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Samsung M32 4G"), any())).thenReturn(m32);
        CatalogDevice catalogA = catalogDevice();
        CatalogDevice catalogM = catalogDevice();
        when(catalog.addDevice(any(), eq("A32"), any(), any(), any(), eq(actorId), eq(groupId))).thenReturn(catalogA);
        when(catalog.addDevice(any(), eq("M32"), any(), any(), any(), eq(actorId), eq(groupId))).thenReturn(catalogM);
        when(importJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = importService.importCompatibility(shopId, actorId, groupId, new ImportRequest(
                "Samsung",
                "Samsung A32 4G = Samsung M32 4G\n# skip me",
                "paste",
                categoryId));

        assertThat(result.groups()).isEqualTo(1);
        assertThat(result.devices()).isEqualTo(2);
        verify(families).create(eq(groupId), eq("TEMPERED_GLASS"), any(),
                eq(List.of(catalogA.getId(), catalogM.getId())), eq(actorId));
    }

    @Test
    void numberedPasteLinesStillImport() {
        Category category = new Category();
        category.setCode("TEMPERED_GLASS");
        when(categories.findByIdAndShopId(categoryId, shopId)).thenReturn(Optional.of(category));
        DeviceModel nine = device("9", null);
        DeviceModel nineA = device("9A", null);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Redmi 9"), any())).thenReturn(nine);
        when(deviceService.findOrCreateFromText(eq(shopId), eq("Redmi 9A"), any())).thenReturn(nineA);
        when(catalog.addDevice(any(), any(), any(), any(), any(), eq(actorId), eq(groupId)))
                .thenAnswer(invocation -> catalogDevice());
        when(importJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        importService.importCompatibility(shopId, actorId, groupId, new ImportRequest(
                null, "3. Redmi 9 = Redmi 9A ✅", "paste", categoryId));

        verify(families, times(1)).create(eq(groupId), eq("TEMPERED_GLASS"), any(), any(), eq(actorId));
    }

    @Test
    void requiresACategory() {
        assertThatThrownBy(() -> importService.importCompatibility(shopId, actorId, groupId,
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

    private CatalogDevice catalogDevice() {
        CatalogDevice device = new CatalogDevice();
        device.setId(UUID.randomUUID());
        return device;
    }
}
