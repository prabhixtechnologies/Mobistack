package com.fixflow.commons.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilds the catalog snapshot every ten minutes. The Redis lock is what keeps a second instance
 * from reading Postgres at the same time. Readers stay on the published copy while this runs.
 */
@Slf4j
@Component
@Order(500)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fixflow.redis.enabled", havingValue = "true")
public class CatalogSnapshotJob implements ApplicationRunner {

    static final int REFRESH_EVERY_MINUTES = 10;

    private final CatalogSnapshotLoader loader;
    private final CatalogSnapshotStore store;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(fixedRate = REFRESH_EVERY_MINUTES, initialDelay = REFRESH_EVERY_MINUTES, timeUnit = TimeUnit.MINUTES)
    public void scheduled() {
        refresh();
    }

    public void refresh() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String token = UUID.randomUUID().toString();
        try {
            if (!store.tryLock(token)) {
                log.debug("Catalog snapshot refresh is already running on another instance");
                return;
            }
            try {
                long started = System.nanoTime();
                Map<UUID, CatalogSnapshotDocument> documents = loader.load();
                store.publish(documents);
                int phones = documents.values().stream().mapToInt(document -> document.devices().size()).sum();
                int edges = documents.values().stream().mapToInt(document -> document.fitments().size()).sum();
                log.info("Published catalog snapshot for {} groups, {} phones, {} fitments in {} ms",
                        documents.size(), phones, edges, (System.nanoTime() - started) / 1_000_000);
            } finally {
                store.unlock(token);
            }
        } catch (RuntimeException ex) {
            log.error("Catalog snapshot refresh failed", ex);
        } finally {
            running.set(false);
        }
    }
}
