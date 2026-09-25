package com.fixflow.report.web;

import com.fixflow.report.service.ReportService;
import com.fixflow.report.service.ReportService.Range;
import com.fixflow.report.service.ReportService.ReportBundle;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/mobistack/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize(Authorize.REPORT_READ)
    public ReportBundle bundle(@RequestParam(required = false) String range,
                               @RequestParam(required = false) Instant from,
                               @RequestParam(required = false) Instant to) {
        Range resolved = ReportService.resolve(range, from, to, ZoneId.of("Asia/Kolkata"));
        return reportService.bundle(CurrentUser.shopId(), resolved);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize(Authorize.REPORT_EXPORT)
    public ResponseEntity<String> export(@RequestParam(required = false) String range,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        Range resolved = ReportService.resolve(range, from, to, ZoneId.of("Asia/Kolkata"));
        ReportBundle bundle = reportService.bundle(CurrentUser.shopId(), resolved);
        String csv = """
                metric,value
                sales,%s
                profit,%s
                transactions,%s
                purchases,%s
                repair_revenue,%s
                pending_repairs,%s
                dead_stock_rows,%s
                """.formatted(bundle.sales().sales(), bundle.sales().profit(), bundle.sales().transactions(),
                bundle.purchases().sales(), bundle.repairRevenue(), bundle.repairs().pending(),
                bundle.deadStock().size());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fixflow-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
