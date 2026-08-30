package com.fixflow.imports;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.DeviceLabels;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.service.CompatibilityGroupService;
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

    private final DeviceService deviceService;
    private final CompatibilityGroupService compatibilityGroupService;
    private final AuditService auditService;
    private final ImportJobRepository importJobRepository;
    private final ObjectMapper objectMapper;

    public record ImportRequest(String brand, String text, String sourceName, UUID categoryId) {
    }

    public record ImportResult(int groups, int devices, int aliases, List<String> warnings) {
    }

    @Transactional
    public ImportResult importCompatibility(UUID shopId, ImportRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw ApiException.businessRule("Nothing to import.");
        }
        if (request.categoryId() == null) {
            throw ApiException.businessRule("Pick a part category for this list.");
        }
        String defaultBrand = request.brand() == null || request.brand().isBlank() ? null : request.brand().trim();
        int groups = 0;
        int devices = 0;
        List<String> warnings = new ArrayList<>();
        List<List<String>> lines = parse(request.text(), warnings);
        for (List<String> names : lines) {
            if (names.size() < 2) {
                warnings.add("Skipped (need at least two models): " + String.join(" = ", names));
                continue;
            }
            List<UUID> deviceIds = new ArrayList<>();
            DeviceModel primary = null;
            for (String name : names) {
                DeviceModel device = deviceService.findOrCreateFromText(shopId, name, defaultBrand);
                devices++;
                deviceIds.add(device.getId());
                if (primary == null) {
                    primary = device;
                }
            }
            String groupName = trimName(primary == null
                    ? names.get(0)
                    : DeviceLabels.display(primary.getBrand().getName(), primary.getName(), primary.getVariant()));
            compatibilityGroupService.create(shopId, new CompatibilityGroupRequest(
                    null,
                    groupName,
                    request.categoryId(),
                    "Imported",
                    false,
                    true,
                    deviceIds,
                    null));
            groups++;
        }
        ImportResult result = new ImportResult(groups, devices, 0, warnings);
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
            line = line.replaceFirst("^\\d+[.)]\\s*", "");
            String[] parts = line.contains(",") && !line.contains("=") ? line.split(",") : line.split("=");
            Set<String> names = new LinkedHashSet<>();
            for (String part : parts) {
                String name = part.trim().replaceAll("[✅✔]\\s*$", "").trim();
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

    private static String trimName(String name) {
        if (name.length() <= 160) {
            return name;
        }
        return name.substring(0, 160);
    }
}
