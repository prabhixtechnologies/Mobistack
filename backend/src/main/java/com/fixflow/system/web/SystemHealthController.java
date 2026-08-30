package com.fixflow.system.web;

import com.fixflow.security.Authorize;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Operational snapshot for the in-app System health screen.
 *
 * {@code /actuator/health} is deliberately kept detail-free and public so
 * container probes can reach it without leaking the component topology. This
 * endpoint carries the detail instead and sits behind {@code SETTINGS_READ}, so
 * only workspace administrators see dependency status and runtime numbers.
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System")
public class SystemHealthController {

    /** Micrometer reports "no such meter" as an absent bean rather than zero. */
    private static final double UNKNOWN = -1d;

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;

    public record ComponentHealth(String name, String status, String detail) {
    }

    public record Runtime(
            double uptimeSeconds,
            double cpuUsage,
            long heapUsedBytes,
            long heapMaxBytes,
            double dbPoolActive,
            double dbPoolMax,
            long requestCount,
            long serverErrorCount,
            double meanRequestMillis
    ) {
    }

    public record SystemStatus(String status, List<ComponentHealth> components, Runtime runtime) {
    }

    @GetMapping("/health")
    @PreAuthorize(Authorize.SETTINGS_READ)
    @Operation(summary = "Dependency health and runtime metrics for this instance")
    public SystemStatus health() {
        HealthDescriptor root = healthEndpoint.health();
        return new SystemStatus(root.getStatus().getCode(), components(root), runtime());
    }

    private List<ComponentHealth> components(HealthDescriptor root) {
        List<ComponentHealth> components = new ArrayList<>();
        if (root instanceof CompositeHealthDescriptor composite) {
            Map<String, HealthDescriptor> children = composite.getComponents();
            if (children != null) {
                children.forEach((name, child) -> components.add(
                        new ComponentHealth(name, child.getStatus().getCode(), describe(child))));
            }
        }
        components.sort(Comparator.comparing(ComponentHealth::name));
        return components;
    }

    /**
     * Names the implementation behind a component without echoing the whole detail
     * map, which can carry connection strings and version banners.
     */
    private String describe(HealthDescriptor component) {
        // A leaf is an IndicatedHealthDescriptor rather than a Health: Health is still what an
        // indicator returns, but the endpoint wraps it before handing it out, and only the wrapper
        // carries the details map. Details are also absent, not empty, when the endpoint's group
        // configuration withholds them, so the map is checked rather than dereferenced.
        if (component instanceof IndicatedHealthDescriptor indicated) {
            Map<String, Object> details = indicated.getDetails();
            if (details == null) {
                return null;
            }
            Object database = details.get("database");
            if (database != null) {
                return String.valueOf(database);
            }
            Object version = details.get("version");
            if (version != null) {
                return String.valueOf(version);
            }
        }
        return null;
    }

    private Runtime runtime() {
        List<Timer> requestTimers = new ArrayList<>(meterRegistry.find("http.server.requests").timers());
        long requests = requestTimers.stream().mapToLong(Timer::count).sum();
        long serverErrors = requestTimers.stream()
                .filter(timer -> "SERVER_ERROR".equals(timer.getId().getTag("outcome")))
                .mapToLong(Timer::count)
                .sum();
        double totalMillis = requestTimers.stream()
                .mapToDouble(timer -> timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .sum();

        return new Runtime(
                gauge("process.uptime"),
                gauge("system.cpu.usage"),
                (long) sumHeap("jvm.memory.used"),
                (long) sumHeap("jvm.memory.max"),
                gauge("hikaricp.connections.active"),
                gauge("hikaricp.connections.max"),
                requests,
                serverErrors,
                requests == 0 ? 0d : totalMillis / requests
        );
    }

    private double gauge(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        return gauge == null ? UNKNOWN : gauge.value();
    }

    private double sumHeap(String name) {
        return meterRegistry.find(name).tag("area", "heap").gauges().stream()
                .mapToDouble(Gauge::value)
                .filter(value -> value >= 0)
                .sum();
    }
}
