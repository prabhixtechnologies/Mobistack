package com.fixflow.flags.web;

import com.fixflow.flags.service.FeatureFlagService;
import com.fixflow.flags.service.FeatureFlagService.FlagCard;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
@Tag(name = "Feature flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    public List<FlagCard> mine() {
        return featureFlagService.resolved(CurrentUser.shopId());
    }
}
