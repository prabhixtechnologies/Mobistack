package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CatalogFamilyServiceTest {

    @Test
    void createNeedsTwoPhones() {
        CatalogFamilyService service = new CatalogFamilyService(
                mock(CommonsCatalogService.class),
                mock(com.fixflow.commons.repository.CatalogComponentRepository.class),
                mock(com.fixflow.commons.repository.CatalogFitmentRepository.class),
                mock(com.fixflow.commons.repository.CatalogDeviceRepository.class),
                mock(CatalogCache.class));
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "TEMPERED_GLASS", "Glass",
                List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("two models");
    }
}
