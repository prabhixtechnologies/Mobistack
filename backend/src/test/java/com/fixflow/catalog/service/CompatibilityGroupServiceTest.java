package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.CompatibilityGroup;
import com.fixflow.catalog.domain.CompatibilityGroupDevice;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.CopyGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupMembershipRequest;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.CompatibilityChangeRequestRepository;
import com.fixflow.catalog.repository.CompatibilityGroupDeviceRepository;
import com.fixflow.catalog.repository.CompatibilityGroupRepository;
import com.fixflow.catalog.repository.CompatibilityHistoryRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductCompatibilityRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.flags.service.FeatureFlagService;
import com.fixflow.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompatibilityGroupServiceTest {

    @Mock
    private CompatibilityGroupRepository groupRepository;
    @Mock
    private CompatibilityGroupDeviceRepository groupDeviceRepository;
    @Mock
    private DeviceModelRepository deviceModelRepository;
    @Mock
    private DeviceService deviceService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductCompatibilityRepository productCompatibilityRepository;
    @Mock
    private CompatibilityHistoryRepository historyRepository;
    @Mock
    private CompatibilityChangeRequestRepository changeRequestRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private AuditService auditService;

    private CompatibilityGroupService service;
    private UUID shopId;
    private UUID groupId;
    private CompatibilityGroup group;

    @BeforeEach
    void setUp() {
        service = new CompatibilityGroupService(groupRepository, groupDeviceRepository, deviceModelRepository,
                deviceService, categoryRepository, productCompatibilityRepository, historyRepository,
                changeRequestRepository, shopRepository, featureFlagService, auditService);
        shopId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        group = new CompatibilityGroup();
        group.setId(groupId);
        group.setShopId(shopId);
        group.setName("Redmi 9 family glass");
        group.setCode("REDMI_9_GLASS");
        group.setActive(true);
    }

    @Test
    void copyDuplicatesMembershipAndClearsVerified() {
        UUID deviceId = UUID.randomUUID();
        CompatibilityGroupDevice link = new CompatibilityGroupDevice();
        link.setDeviceModelId(deviceId);
        link.setPrimaryDevice(true);

        when(groupRepository.findByIdAndShopId(groupId, shopId)).thenReturn(Optional.of(group));
        when(groupDeviceRepository.findByCompatibilityGroupId(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(groupId) ? List.of(link) : List.of();
        });
        when(groupRepository.save(any())).thenAnswer(invocation -> {
            CompatibilityGroup saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(productCompatibilityRepository.countByCompatibilityGroupId(any())).thenReturn(0L);
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var copied = service.copy(shopId, groupId, new CopyGroupRequest(null, null));

        assertThat(copied.verified()).isFalse();
        assertThat(copied.name()).isEqualTo("Redmi 9 family glass copy");
        ArgumentCaptor<CompatibilityGroupDevice> captor = ArgumentCaptor.forClass(CompatibilityGroupDevice.class);
        verify(groupDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceModelId()).isEqualTo(deviceId);
        assertThat(captor.getValue().isPrimaryDevice()).isTrue();
    }

    @Test
    void deleteHardDeletesWhenNoProductsAreLinked() {
        when(groupRepository.findByIdAndShopId(groupId, shopId)).thenReturn(Optional.of(group));
        when(productCompatibilityRepository.countByCompatibilityGroupId(groupId)).thenReturn(0L);

        service.delete(shopId, groupId);

        verify(groupRepository).delete(group);
        verify(groupRepository, never()).save(group);
    }

    @Test
    void deleteDeactivatesWhenProductsAreLinked() {
        when(groupRepository.findByIdAndShopId(groupId, shopId)).thenReturn(Optional.of(group));
        when(productCompatibilityRepository.countByCompatibilityGroupId(groupId)).thenReturn(2L);

        service.delete(shopId, groupId);

        verify(groupRepository, never()).delete(group);
        verify(groupRepository).save(group);
        assertThat(group.isActive()).isFalse();
    }

    @Test
    void replaceMembershipRebuildsTheLineFromPastedNames() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DeviceModel a32 = device("A32", "4G", first);
        DeviceModel m32 = device("M32", "4G", second);

        when(groupRepository.findByIdAndShopId(groupId, shopId)).thenReturn(Optional.of(group));
        when(shopRepository.findById(shopId)).thenReturn(Optional.empty());
        when(featureFlagService.enabled(shopId, "COMPATIBILITY_APPROVAL")).thenReturn(false);
        when(deviceService.findOrCreateFromText(shopId, "Samsung A32 4G", null)).thenReturn(a32);
        when(deviceService.findOrCreateFromText(shopId, "Samsung M32 4G", null)).thenReturn(m32);
        when(deviceModelRepository.findByIdAndShopId(first, shopId)).thenReturn(Optional.of(a32));
        when(deviceModelRepository.findByIdAndShopId(second, shopId)).thenReturn(Optional.of(m32));
        when(groupDeviceRepository.findByCompatibilityGroupId(groupId)).thenReturn(List.of());
        when(productCompatibilityRepository.countByCompatibilityGroupId(groupId)).thenReturn(0L);

        service.replaceMembership(shopId, groupId, new GroupMembershipRequest(
                null, List.of("Samsung A32 4G", "Samsung M32 4G")));

        verify(groupDeviceRepository).deleteByCompatibilityGroupId(groupId);
        verify(groupDeviceRepository).flush();
        ArgumentCaptor<CompatibilityGroupDevice> captor = ArgumentCaptor.forClass(CompatibilityGroupDevice.class);
        verify(groupDeviceRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(CompatibilityGroupDevice::getDeviceModelId)
                .containsExactly(first, second);
        assertThat(captor.getAllValues().get(0).isPrimaryDevice()).isTrue();
        assertThat(captor.getAllValues().get(1).isPrimaryDevice()).isFalse();
    }

    @Test
    void replaceMembershipRejectsAnEmptyLine() {
        when(groupRepository.findByIdAndShopId(groupId, shopId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.replaceMembership(shopId, groupId,
                new GroupMembershipRequest(List.of(), List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least one model");
    }

    private DeviceModel device(String name, String variant, UUID id) {
        Brand brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setName("Samsung");
        DeviceModel device = new DeviceModel();
        device.setId(id);
        device.setShopId(shopId);
        device.setBrand(brand);
        device.setName(name);
        device.setVariant(variant);
        return device;
    }
}
