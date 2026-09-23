package com.fixflow.commons.web;

import com.fixflow.billing.service.BillingService;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.service.CommonsCatalogService;
import com.fixflow.commons.service.ContributionService;
import com.fixflow.group.service.SharingGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The shared catalog is free. A COMPATIBILITY plan gate here would lock the acquisition
 * half of the product behind the paid half.
 */
@ExtendWith(MockitoExtension.class)
class CommonsControllerBillingIsolationTest {

    @Mock
    private CommonsCatalogService catalog;
    @Mock
    private ContributionService contributions;
    @Mock
    private com.fixflow.shop.repository.ShopRepository shops;
    @Mock
    private SharingGroupService groups;
    @Mock
    private BillingService billingService;

    @Test
    void listingBrandsDoesNotConsultTheCompatibilityPlan() {
        when(catalog.listBrands()).thenReturn(List.of(new CatalogBrand()));

        CommonsController controller = new CommonsController(catalog, contributions, shops, groups);
        assertThat(controller.brands()).hasSize(1);

        verify(catalog).listBrands();
        verifyNoInteractions(billingService);
        verify(shops, never()).count();
    }

    @Test
    void commonsControllerDoesNotDependOnBilling() {
        assertThat(CommonsController.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(BillingService.class, com.fixflow.billing.service.PlanService.class);
    }
}
