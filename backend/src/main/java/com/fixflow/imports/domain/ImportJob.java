package com.fixflow.imports.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "import_jobs")
public class ImportJob extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "source_name", length = 160)
    private String sourceName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false)
    private Map<String, Object> resultJson = Map.of();
}
