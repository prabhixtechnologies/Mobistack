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
    @Operation(summary = "Search devices, aliases, SKUs, barcodes and parts in one query")
    public GlobalSearchResponse search(@RequestParam("q") String query,
                                       @RequestParam(defaultValue = "NORMAL") PricingFlag flag) {
        return searchService.search(CurrentUser.shopId(), query, flag);
    }
}
