package de.campusshop.dispatch.assurance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.dispatch.client.SimulationTariffClient;
import de.campusshop.dispatch.dto.*;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.repository.*;
import de.campusshop.dispatch.service.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Diagnostic experiment: passing means predictions were checked, not that torn states are safe. */
class FailureCutExperimentTest {
    enum Point { LOOKUP, ARCHIVE_PUT, INDEX_PUT, ARCHIVE_REMOVE, INDEX_REMOVE }
    enum Cut { BEFORE, AFTER }
    record Fault(Point point, Cut cut) { }
    record Scenario(String name, boolean deletion, boolean expectedArchive, boolean expectedIndex,
                    String outcome, List<Fault> faults) { }
    record Facet(int parcels, boolean unicode, boolean padding) {
        String id() { return parcels + "-" + unicode + "-" + padding; }
    }
    static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    static final Map<Logger, Level> LOG_LEVELS = new LinkedHashMap<>();
    static final List<Scenario> SCENARIOS = List.of(
        scenario("register-control", false, true, true, "CREATED"),
        scenario("lookup-before", false, false, false, "ERROR", fault(Point.LOOKUP, Cut.BEFORE)),
        scenario("lookup-after", false, false, false, "ERROR", fault(Point.LOOKUP, Cut.AFTER)),
        scenario("archive-before", false, false, false, "ERROR", fault(Point.ARCHIVE_PUT, Cut.BEFORE)),
        scenario("archive-after", false, true, false, "ERROR", fault(Point.ARCHIVE_PUT, Cut.AFTER)),
        scenario("index-before", false, false, false, "ERROR", fault(Point.INDEX_PUT, Cut.BEFORE)),
        scenario("index-after", false, false, true, "ERROR", fault(Point.INDEX_PUT, Cut.AFTER)),
        scenario("index-before-cleanup-before", false, true, false, "ERROR",
            fault(Point.INDEX_PUT, Cut.BEFORE), fault(Point.ARCHIVE_REMOVE, Cut.BEFORE)),
        scenario("index-after-cleanup-before", false, true, true, "ERROR",
            fault(Point.INDEX_PUT, Cut.AFTER), fault(Point.ARCHIVE_REMOVE, Cut.BEFORE)),
        scenario("index-before-cleanup-after", false, false, false, "ERROR",
            fault(Point.INDEX_PUT, Cut.BEFORE), fault(Point.ARCHIVE_REMOVE, Cut.AFTER)),
        scenario("index-after-cleanup-after", false, false, true, "ERROR",
            fault(Point.INDEX_PUT, Cut.AFTER), fault(Point.ARCHIVE_REMOVE, Cut.AFTER)),
        scenario("delete-control", true, false, false, "DELETED"),
        scenario("delete-archive-before", true, true, true, "ERROR", fault(Point.ARCHIVE_REMOVE, Cut.BEFORE)),
        scenario("delete-archive-after", true, false, true, "ERROR", fault(Point.ARCHIVE_REMOVE, Cut.AFTER)),
        scenario("delete-index-before", true, false, true, "ERROR", fault(Point.INDEX_REMOVE, Cut.BEFORE)),
        scenario("delete-index-after", true, false, false, "ERROR", fault(Point.INDEX_REMOVE, Cut.AFTER))
    );

    static Fault fault(Point point, Cut cut) { return new Fault(point, cut); }
    static Scenario scenario(String name, boolean deletion, boolean archive, boolean index,
                             String outcome, Fault... faults) {
        return new Scenario(name, deletion, archive, index, outcome, List.of(faults));
    }

    @BeforeAll static void quietExpectedInjectedExceptions() {
        for (Class<?> type : List.of(ShipmentRegistrationService.class, ShipmentQueryService.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            LOG_LEVELS.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
    }

    @AfterAll static void export() throws Exception {
        LOG_LEVELS.forEach(Logger::setLevel);
        Path destination = Path.of("target/evidence/failure-cuts.json");
        Files.createDirectories(destination.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.toFile(),
            Map.of("scope", "actual service with deterministic in-memory fault adapters; not AWS",
                "planned_scenarios", SCENARIOS.size(), "planned_facets", 12,
                "safety_claim", "Known torn states are findings; characterization passes are not safety passes",
                "cases", RESULTS));
    }

    @TestFactory Stream<DynamicTest> everyDeclaredFailureCutAndPayloadFacet() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int count : List.of(1, 2, 5)) {
            for (boolean unicode : List.of(false, true)) {
                for (boolean padding : List.of(false, true)) {
                    Facet facet = new Facet(count, unicode, padding);
                    for (Scenario scenario : SCENARIOS) {
                        tests.add(DynamicTest.dynamicTest(scenario.name() + "/" + facet.id(),
                            () -> execute(scenario, facet)));
                    }
                }
            }
        }
        return tests.stream();
    }

    private void execute(Scenario scenario, Facet facet) {
        Fixture fixture = new Fixture();
        byte[] bytes = rawFacet(facet);
        DispatchManifest input = new DispatchManifest(MANIFEST, manifest().carrier(), manifest().dispatchDate(),
            "EDI-01", Collections.nCopies(facet.parcels(), new ParcelDto("TRF-1", dec("2.5"))));
        String ref = REF;
        if (scenario.deletion()) ref = fixture.registration.register(input, bytes).shipmentRef();
        fixture.trace.clear();
        fixture.faults.addAll(scenario.faults());
        String outcome = scenario.deletion() ? "DELETED" : "CREATED";
        RuntimeException failure = null;
        try {
            if (scenario.deletion()) fixture.queries.delete(ref);
            else ref = fixture.registration.register(input, bytes).shipmentRef();
        } catch (RuntimeException expected) {
            failure = expected;
            outcome = "ERROR";
        }
        fixture.armed = false;
        boolean archive = !fixture.archive.objects.isEmpty();
        boolean index = !fixture.repository.listByCarrier(CARRIER).isEmpty();
        assertThat(outcome).isEqualTo(scenario.outcome());
        assertThat(archive).as("archive bit").isEqualTo(scenario.expectedArchive());
        assertThat(index).as("index bit").isEqualTo(scenario.expectedIndex());
        assertThat(fixture.fired).containsExactlyInAnyOrderElementsOf(scenario.faults());
        for (byte[] stored : fixture.archive.objects.values()) assertThat(stored).isEqualTo(bytes);
        if (fixture.lastItem != null) ref = fixture.lastItem.shipmentRef();
        Map<String, String> observations = new LinkedHashMap<>();
        observations.put("return", outcome);
        try { fixture.queries.find(ref); observations.put("summary", "FOUND"); }
        catch (ShipmentNotFoundException absent) { observations.put("summary", "ABSENT"); }
        try {
            assertThat(fixture.queries.manifest(ref)).isEqualTo(bytes);
            observations.put("manifest", "EXACT_BYTES");
        } catch (ShipmentNotFoundException absent) { observations.put("manifest", "ABSENT"); }
        observations.put("archive-inventory", archive ? "PRESENT" : "ABSENT");
        observations.put("index-inventory", index ? "PRESENT" : "ABSENT");
        assertThat(observations.get("summary")).isEqualTo(index ? "FOUND" : "ABSENT");
        assertThat(observations.get("manifest")).isEqualTo(archive && index ? "EXACT_BYTES" : "ABSENT");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario.name());
        result.put("facet", Map.of("parcels", facet.parcels(), "unicode", facet.unicode(), "padding", facet.padding()));
        result.put("archive", archive);
        result.put("index", index);
        result.put("coherent", archive == index);
        result.put("finding", archive == index ? "COHERENT" : archive ? "ORPHAN_ARCHIVE" : "DANGLING_INDEX");
        result.put("outcome", outcome);
        result.put("expected_state", List.of(scenario.expectedArchive(), scenario.expectedIndex()));
        result.put("fired", fixture.fired.stream().map(Object::toString).toList());
        result.put("declared_faults", scenario.faults().stream().map(Object::toString).toList());
        result.put("suppressed_exceptions", failure == null ? 0 : failure.getSuppressed().length);
        result.put("trace", List.copyOf(fixture.trace));
        result.put("observations", observations);
        result.put("raw_bytes_checked", true);
        RESULTS.add(result);
    }

    @Test void stateOracleCalibrationIncludesBothKindsOfTornState() {
        List<String> accepted = new ArrayList<>(), rejected = new ArrayList<>();
        for (boolean archive : List.of(false, true)) for (boolean index : List.of(false, true)) {
            String state = (archive ? "1" : "0") + (index ? "1" : "0");
            (archive == index ? accepted : rejected).add(state);
        }
        assertThat(accepted).containsExactly("00", "11");
        assertThat(rejected).containsExactly("01", "10");
    }

    @Test void rawByteOracleRejectsASemanticallyEquivalentButReformattedBody() throws Exception {
        byte[] original = rawFacet(new Facet(1, true, false));
        byte[] padded = rawFacet(new Facet(1, true, true));
        assertThat(Arrays.equals(original, padded)).isFalse();
        ObjectMapper json = new ObjectMapper();
        assertThat(json.readTree(original)).isEqualTo(json.readTree(padded));
        assertThatThrownBy(() -> assertThat(padded).isEqualTo(original)).isInstanceOf(AssertionError.class);
    }

    static byte[] rawFacet(Facet facet) {
        String parcel = "{\"tariffCode\":\"TRF-1\",\"weightKg\":2.5}";
        String json = "{\"manifestNo\":\"" + MANIFEST + "\",\"carrier\":{\"carrierId\":\"" + CARRIER
            + "\",\"name\":\"Campus Couriers\",\"contactEmail\":\"ops@example.org\"},"
            + "\"dispatchDate\":\"2026-11-20\",\"originWarehouse\":\"EDI-01\",\"parcels\":["
            + String.join(",", Collections.nCopies(facet.parcels(), parcel)) + "],\"unknown\":\""
            + (facet.unicode() ? "café / 原始证据" : "preserve raw bytes") + "\"}";
        return (facet.padding() ? " \n\t" + json + "\r\n " : json).getBytes(StandardCharsets.UTF_8);
    }

    static class InjectedFailure extends RuntimeException {
        InjectedFailure(Fault fault) { super("Injected " + fault); }
    }

    static class Fixture {
        boolean armed = true;
        final List<Fault> faults = new ArrayList<>(), fired = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        ShipmentItem lastItem;
        final TrackedArchive archive = new TrackedArchive();
        final InMemoryShipmentRepository repository = new InMemoryShipmentRepository() {
            @Override public boolean manifestExists(String manifest) {
                before(Point.LOOKUP);
                boolean result = super.manifestExists(manifest);
                after(Point.LOOKUP);
                return result;
            }
            @Override public void put(ShipmentItem item) {
                lastItem = item;
                before(Point.INDEX_PUT);
                super.put(item);
                after(Point.INDEX_PUT);
            }
            @Override public void remove(String ref) {
                before(Point.INDEX_REMOVE);
                super.remove(ref);
                after(Point.INDEX_REMOVE);
            }
        };
        final ShipmentRegistrationService registration = new ShipmentRegistrationService(repository, archive,
            new SimulationTariffClient(), new ChargeCalculator(), new ManifestMapper(),
            Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC));
        final ShipmentQueryService queries = new ShipmentQueryService(repository, archive, new ManifestMapper());
        void before(Point point) { trace.add(point + ":BEFORE"); trip(point, Cut.BEFORE); }
        void after(Point point) { trace.add(point + ":AFTER"); trip(point, Cut.AFTER); }
        void trip(Point point, Cut cut) {
            Fault fault = fault(point, cut);
            if (armed && faults.contains(fault) && !fired.contains(fault)) {
                fired.add(fault);
                throw new InjectedFailure(fault);
            }
        }
        class TrackedArchive implements ManifestArchive {
            final Map<String, byte[]> objects = new LinkedHashMap<>();
            @Override public void archive(String key, byte[] bytes) {
                before(Point.ARCHIVE_PUT);
                objects.put(key, bytes.clone());
                after(Point.ARCHIVE_PUT);
            }
            @Override public Optional<byte[]> fetch(String key) {
                return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
            }
            @Override public void remove(String key) {
                before(Point.ARCHIVE_REMOVE);
                objects.remove(key);
                after(Point.ARCHIVE_REMOVE);
            }
        }
    }
}
