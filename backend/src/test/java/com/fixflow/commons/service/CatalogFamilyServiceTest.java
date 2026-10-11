package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.commons.repository.CatalogFitmentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogFamilyServiceTest {

    @Test
    void createNeedsTwoPhones() {
        CatalogFamilyService service = new CatalogFamilyService(
                mock(CommonsCatalogService.class),
                mock(com.fixflow.commons.repository.CatalogComponentRepository.class),
                mock(com.fixflow.commons.repository.CatalogFitmentRepository.class),
                mock(com.fixflow.commons.repository.CatalogDeviceRepository.class),
                mock(CatalogCache.class),
                mock(CatalogSnapshotStore.class));
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "TEMPERED_GLASS", "Glass",
                List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("two models");
    }

    @Test
    void categoryBrowseKeepsBrandsWhosePhonesHaveNoMappedFamily() {
        UUID groupId = UUID.randomUUID();
        UUID mappedBrandId = UUID.randomUUID();
        UUID unmappedBrandId = UUID.randomUUID();
        CommonsCatalogService catalog = mock(CommonsCatalogService.class);
        CatalogComponentRepository components = mock(CatalogComponentRepository.class);
        CatalogFitmentRepository fitments = mock(CatalogFitmentRepository.class);
        CatalogDeviceRepository devices = mock(CatalogDeviceRepository.class);
        CatalogFamilyService service = new CatalogFamilyService(
                catalog, components, fitments, devices, mock(CatalogCache.class), mock(CatalogSnapshotStore.class));

        CatalogBrand mappedBrand = brand(mappedBrandId, "Samsung");
        CatalogBrand unmappedBrand = brand(unmappedBrandId, "Apple");
        CatalogDeviceRepository.BrandCount mappedTotal = brandCount(mappedBrandId, 300);
        CatalogDeviceRepository.BrandCount unmappedTotal = brandCount(unmappedBrandId, 50);
        CatalogDeviceRepository.BrandCount mappedBattery = brandCount(mappedBrandId, 120);
        when(catalog.listBrands(groupId)).thenReturn(List.of(mappedBrand, unmappedBrand));
        when(devices.countByBrand(groupId)).thenReturn(List.of(mappedTotal, unmappedTotal));
        when(devices.countByBrandAndCategory(groupId, "BATTERY")).thenReturn(List.of(mappedBattery));

        List<CatalogFamilyService.BrandIndex> rows = service.brandsInCategory(groupId, "battery");

        assertThat(rows).extracting(CatalogFamilyService.BrandIndex::name)
                .containsExactly("Samsung", "Apple");
        assertThat(rows.get(1).deviceCount()).isEqualTo(50);
        assertThat(rows.get(1).mappedDeviceCount()).isZero();
    }

    private static CatalogBrand brand(UUID id, String name) {
        CatalogBrand brand = mock(CatalogBrand.class);
        when(brand.getId()).thenReturn(id);
        when(brand.getName()).thenReturn(name);
        return brand;
    }

    private static CatalogDeviceRepository.BrandCount brandCount(UUID brandId, long deviceCount) {
        CatalogDeviceRepository.BrandCount count = mock(CatalogDeviceRepository.BrandCount.class);
        when(count.getBrandId()).thenReturn(brandId);
        when(count.getDeviceCount()).thenReturn(deviceCount);
        return count;
    }
}
