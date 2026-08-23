package com.fixflow.imports.repository;

import com.fixflow.imports.domain.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Page<ImportJob> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);
}
