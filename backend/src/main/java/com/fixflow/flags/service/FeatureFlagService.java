package com.fixflow.flags.service;

import com.fixflow.flags.domain.FeatureFlag;
import com.fixflow.flags.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    public record FlagCard(String code, boolean enabled, boolean overridden) {
    }

    private final FeatureFlagRepository flagRepository;

    @Cacheable(cacheNames = "flags", key = "T(String).valueOf(#shopId) + ':' + #code")
    @Transactional(readOnly = true)
    public boolean enabled(UUID shopId, String code) {
        if (shopId != null) {
            var shopFlag = flagRepository.findByShopIdAndCode(shopId, code);
            if (shopFlag.isPresent()) {
                return shopFlag.get().isEnabled();
            }
        }
        return flagRepository.findByShopIdIsNullAndCode(code).map(FeatureFlag::isEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<FlagCard> resolved(UUID shopId) {
        Map<String, FlagCard> flags = new LinkedHashMap<>();
        flagRepository.findByShopIdIsNull()
                .forEach(flag -> flags.put(flag.getCode(), new FlagCard(flag.getCode(), flag.isEnabled(), false)));
        if (shopId != null) {
            flagRepository.findByShopId(shopId)
                    .forEach(flag -> flags.put(flag.getCode(), new FlagCard(flag.getCode(), flag.isEnabled(), true)));
        }
        return List.copyOf(flags.values());
    }

    @CacheEvict(cacheNames = "flags", allEntries = true)
    @Transactional
    public FlagCard upsert(UUID shopId, String code, boolean enabled) {
        FeatureFlag flag = (shopId == null
                ? flagRepository.findByShopIdIsNullAndCode(code)
                : flagRepository.findByShopIdAndCode(shopId, code))
                .orElseGet(FeatureFlag::new);
        flag.setShopId(shopId);
        flag.setCode(code.trim().toUpperCase());
        flag.setEnabled(enabled);
        flagRepository.save(flag);
        return new FlagCard(flag.getCode(), flag.isEnabled(), shopId != null);
    }
}
