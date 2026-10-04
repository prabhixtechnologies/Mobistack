package com.fixflow.config;

import com.fixflow.commons.service.CatalogFamilyService.CategoryIndex;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheSerializerTest {

    @Test
    void catalogRowsComeBackAsRecords() {
        var serializer = PlatformCacheConfig.redisSerializer(JsonMapper.builder().build());
        List<CategoryIndex> rows = new ArrayList<>();
        rows.add(new CategoryIndex("BATTERY", "Battery", 70, 12, 4));

        Object restored = serializer.deserialize(serializer.serialize(rows));

        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).singleElement().isInstanceOf(CategoryIndex.class);
        assertThat(((CategoryIndex) ((List<?>) restored).getFirst()).code()).isEqualTo("BATTERY");
        assertThat(((CategoryIndex) ((List<?>) restored).getFirst()).familyCount()).isEqualTo(12);
    }
}
