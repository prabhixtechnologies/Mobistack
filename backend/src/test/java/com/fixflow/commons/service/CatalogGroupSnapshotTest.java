package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.service.CatalogFamilyService.BrandIndex;
import com.fixflow.commons.service.CatalogFamilyService.FamilyView;
import com.fixflow.commons.service.CatalogSnapshotDocument.BrandRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.ComponentRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.DeviceRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.FitmentRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogGroupSnapshotTest {

    @Test
    void searchUsesNameAndAliasAndLeavesFactoryCodesToTheBrandSearch() {
        UUID group = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        UUID popular = UUID.randomUUID();
        UUID named = UUID.randomUUID();
        CatalogGroupSnapshot snapshot = CatalogGroupSnapshot.from(group, new CatalogSnapshotDocument(
                List.of(new BrandRow(brand, "Samsung", null)),
                List.of(
                        new DeviceRow(named, brand, "Galaxy A10", null, "SM-A105F", 2019, 2, List.of("a10s")),
                        new DeviceRow(popular, brand, "Galaxy A20", null, "SM-A205F", 2019, 40, List.of())),
                List.of(),
                List.of()));

        assertThat(snapshot.searchDevices("a20", 0, 10).getContent())
                .extracting(CatalogDevice::getId)
                .containsExactly(popular);
        assertThat(snapshot.searchDevices("A10S", 0, 10).getContent())
                .extracting(CatalogDevice::getId)
                .containsExactly(named);
        assertThat(snapshot.searchDevices("sm-a", 0, 10).getContent()).isEmpty();
        assertThat(snapshot.searchInBrand(brand, "sm-a", 0, 10).getContent())
                .extracting(CatalogDevice::getName)
                .containsExactly("Galaxy A10", "Galaxy A20");
        assertThat(snapshot.searchDevices("a2", 0, 1).getContent())
                .extracting(CatalogDevice::getId)
                .containsExactly(popular);
        assertThatThrownBy(() -> snapshot.searchDevices("a", 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("two characters");
    }

    @Test
    void brandListKeepsPhonesThatHaveNoFamilyInTheSpare() {
        UUID group = UUID.randomUUID();
        UUID samsung = UUID.randomUUID();
        UUID apple = UUID.randomUUID();
        UUID phone = UUID.randomUUID();
        UUID component = UUID.randomUUID();
        CatalogGroupSnapshot snapshot = CatalogGroupSnapshot.from(group, new CatalogSnapshotDocument(
                List.of(new BrandRow(samsung, "Samsung", null), new BrandRow(apple, "Apple", null)),
                List.of(
                        new DeviceRow(phone, samsung, "A12", null, null, null, 0, List.of()),
                        new DeviceRow(UUID.randomUUID(), apple, "iPhone 11", null, null, null, 0, List.of())),
                List.of(new ComponentRow(component, "BATTERY", "Samsung A12 Battery", null, Map.of())),
                List.of(new FitmentRow(UUID.randomUUID(), component, phone, "COMPATIBLE", 1, 0, false, false))));

        List<BrandIndex> rows = snapshot.brandsInCategory("battery");
        assertThat(rows).extracting(BrandIndex::name).containsExactly("Apple", "Samsung");
        BrandIndex appleRow = rows.get(0);
        assertThat(appleRow.deviceCount()).isEqualTo(1);
        assertThat(appleRow.mappedDeviceCount()).isZero();
        assertThat(rows.get(1).mappedDeviceCount()).isEqualTo(1);
    }

    @Test
    void phoneCompatibilityDropsThePlaceholderCoveredByTheImportedFamily() {
        UUID group = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        UUID phone = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID placeholder = UUID.randomUUID();
        UUID imported = UUID.randomUUID();
        CatalogGroupSnapshot snapshot = CatalogGroupSnapshot.from(group, new CatalogSnapshotDocument(
                List.of(new BrandRow(brand, "Samsung", null)),
                List.of(
                        new DeviceRow(phone, brand, "A12", null, null, null, 0, List.of()),
                        new DeviceRow(other, brand, "A13", null, null, null, 0, List.of())),
                List.of(
                        new ComponentRow(placeholder, "BATTERY", "Samsung A12 Battery", null, Map.of()),
                        new ComponentRow(imported, "BATTERY", "Shared battery", null, Map.of("source", "Combo Support"))),
                List.of(
                        new FitmentRow(UUID.randomUUID(), placeholder, phone, "COMPATIBLE", 1, 0, false, false),
                        new FitmentRow(UUID.randomUUID(), imported, phone, "COMPATIBLE", 2, 0, false, false),
                        new FitmentRow(UUID.randomUUID(), imported, other, "COMPATIBLE", 2, 0, false, false))));

        List<FamilyView> families = snapshot.forDevice(phone, null);
        assertThat(families).extracting(FamilyView::name).containsExactly("Shared battery");
    }

    @Test
    void threeVersionsAreKeptIncludingTheCurrentOne() {
        assertThat(CatalogSnapshotStore.versionsToDrop(List.of("3", "2", "1"))).isEmpty();
        assertThat(CatalogSnapshotStore.versionsToDrop(List.of("4", "3", "2", "1"))).containsExactly("1");
        assertThat(CatalogSnapshotStore.versionsToDrop(List.of("5", "4", "3", "2", "1"))).containsExactly("2", "1");
    }
}
