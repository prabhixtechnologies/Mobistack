package com.fixflow.imports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.DeviceAlias;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.service.BrandService;
import com.fixflow.catalog.service.DeviceService;
import com.fixflow.common.error.ApiException;
import com.fixflow.imports.domain.ImportJob;
import com.fixflow.imports.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final BrandService brandService;
    private final DeviceService deviceService;
    private final AuditService auditService;
    private final ImportJobRepository importJobRepository;
    private final ObjectMapper objectMapper;

    public record ImportRequest(String brand, String text, String sourceName) {
    }

    public record ImportResult(int groups, int devices, int aliases, List<String> warnings) {
    }

    @Transactional
    public ImportResult importCompatibility(UUID shopId, ImportRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw ApiException.businessRule("Nothing to import.");
        }
        String brandName = request.brand() == null || request.brand().isBlank() ? "Generic" : request.brand().trim();
        int groups = 0;
        int devices = 0;
        int aliases = 0;
        List<String> warnings = new ArrayList<>();
        List<List<String>> lines = parse(request.text(), warnings);
        for (List<String> names : lines) {
            if (names.size() < 2) {
                warnings.add("Skipped (need at least two models): " + String.join(" = ", names));
                continue;
            }
            Brand brand = brandService.findOrCreate(shopId, brandName);
            DeviceModel primary = null;
            for (String name : names) {
                DeviceModel device = deviceService.findOrCreate(shopId, brand, name);
                devices++;
                if (primary == null) {
                    primary = device;
                } else if (!device.getId().equals(primary.getId())) {
                    deviceService.saveAlias(shopId, primary.getId(), name, DeviceAlias.Source.IMPORT);
                    aliases++;
                }
            }
            groups++;
        }
        ImportResult result = new ImportResult(groups, devices, aliases, warnings);
        ImportJob job = new ImportJob();
        job.setShopId(shopId);
        job.setKind("COMPATIBILITY");
        job.setStatus("IMPORTED");
        job.setSourceName(request.sourceName() == null ? "paste" : request.sourceName());
        job.setResultJson(asMap(result));
        importJobRepository.save(job);
        auditService.record(AuditAction.IMPORT_COMPLETED, "Import", shopId,
                "Imported %d compatibility lines".formatted(groups));
        return result;
    }

    @Transactional(readOnly = true)
    public Page<ImportJob> jobs(UUID shopId, Pageable pageable) {
        return importJobRepository.findByShopIdOrderByCreatedAtDesc(shopId, pageable);
    }

    public Map<String, Object> asMap(ImportResult result) {
        return Map.of("groups", result.groups(), "devices", result.devices(),
                "aliases", result.aliases(), "warnings", result.warnings());
    }

    private List<List<String>> parse(String text, List<String> warnings) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJson(trimmed, warnings);
        }
        List<List<String>> lines = new ArrayList<>();
        for (String rawLine : trimmed.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.contains(",") && !line.contains("=") ? line.split(",") : line.split("=");
            Set<String> names = new LinkedHashSet<>();
            for (String part : parts) {
                String name = part.trim();
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            lines.add(new ArrayList<>(names));
        }
        return lines;
    }

    private List<List<String>> parseJson(String text, List<String> warnings) {
        try {
            if (text.startsWith("[")) {
                List<List<String>> rows = objectMapper.readValue(text, new TypeReference<>() {
                });
                return rows;
            }
            Map<String, Object> body = objectMapper.readValue(text, new TypeReference<>() {
            });
            Object lines = body.getOrDefault("lines", body.get("groups"));
            if (lines instanceof List<?> list) {
                List<List<String>> parsed = new ArrayList<>();
                for (Object row : list) {
                    if (row instanceof List<?> names) {
                        parsed.add(names.stream().map(String::valueOf).toList());
                    }
                }
                return parsed;
            }
        } catch (Exception ex) {
            warnings.add("Could not parse JSON: " + ex.getMessage());
        }
        return List.of();
    }
}
