package com.fixflow.search.web;

import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.search.dto.SearchDtos.GlobalSearchResponse;
import com.fixflow.search.service.SearchService;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize(Authorize.CATALOG_READ)
    @Operation(summary = "Search devices, aliases, SKUs, barcodes, parts and the shared catalog")
    // q is optional: an empty box is a no-op, not a 400. SearchService already
    // returns an empty payload below two characters; requiring the param made a
    // missed query string surface as a raw Spring error on the catalog page.
    public GlobalSearchResponse search(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "NORMAL") PricingFlag flag) {
        return searchService.search(CurrentUser.shopId(), query, flag);
    }
}
