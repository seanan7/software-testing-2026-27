package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.SimulationTariffClient;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationPerformanceTest {
    @Test void localInMemoryRegistrationMeetsThePredeclaredTimingTarget() throws Exception {
        var repository = new InMemoryShipmentRepository();
        var archive = new InMemoryManifestArchive();
        var service = new ShipmentRegistrationService(repository, archive, new SimulationTariffClient(),
                new ChargeCalculator(), new ManifestMapper(), Clock.systemUTC());
        var queries = new ShipmentQueryService(repository, archive, new ManifestMapper());
        Logger registrationLog = (Logger) LoggerFactory.getLogger(ShipmentRegistrationService.class);
        Logger deletionLog = (Logger) LoggerFactory.getLogger(ShipmentQueryService.class);
        Level registrationLevel = registrationLog.getLevel(), deletionLevel = deletionLog.getLevel();
        registrationLog.setLevel(Level.OFF);
        deletionLog.setLevel(Level.OFF);
        List<Double> samples = new ArrayList<>();
        try {
            for (int i = 0; i < 550; i++) {
                String number = new UUID(20261126L, i + 1).toString();
                var m = new DispatchManifest(number, manifest().carrier(), manifest().dispatchDate(), null, manifest().parcels());
                byte[] body = raw(number, CARRIER);
                long start = System.nanoTime();
                var result = service.register(m, body);
                double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
                assertThat(result.totalCharge()).isEqualByComparingTo("16.99");
                assertThat(archive.fetch(result.manifestKey())).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(body));
                queries.delete(result.shipmentRef());
                if (i >= 50) samples.add(elapsedMs);
            }
        } finally {
            registrationLog.setLevel(registrationLevel);
            deletionLog.setLevel(deletionLevel);
        }
        var sorted = samples.stream().sorted().toList();
        double p95 = sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("workload", "two-parcel registration; in-memory stores; simulated tariffs; sequential; empty store before each operation");
        report.put("warmup", 50); report.put("samples", 500); report.put("failures", 0);
        report.put("target_p95_ms", 100); report.put("p50_ms", sorted.get(249)); report.put("p95_ms", p95);
        report.put("max_ms", sorted.getLast()); report.put("raw_ms", samples);
        Path output = Path.of("target/evidence/performance.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertThat(p95).isLessThan(100.0);
    }
}
