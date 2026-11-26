package de.campusshop.dispatch.assurance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.dispatch.client.SimulationTariffClient;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.repository.*;
import de.campusshop.dispatch.service.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Bounded histories of actual service calls, not a simulation of service control flow. */
class ConcurrentHistoryTest {
    record Operation(int actor, int invocation, int response, String manifest, String outcome) { }
    static final List<Map<String, Object>> RESULTS = new ArrayList<>(), CALIBRATION = new ArrayList<>();
    static final List<List<String>> SCHEDULES = List.of(
        List.of("C0", "P0", "C1", "P1"), List.of("C0", "C1", "P0", "P1"),
        List.of("C0", "C1", "P1", "P0"), List.of("C1", "P1", "C0", "P0"),
        List.of("C1", "C0", "P1", "P0"), List.of("C1", "C0", "P0", "P1"));
    static Logger logger;
    static Level oldLevel;

    @BeforeAll static void quietExpectedOutcomes() {
        logger = (Logger) LoggerFactory.getLogger(ShipmentRegistrationService.class);
        oldLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
    }
    @AfterAll static void export() throws Exception {
        logger.setLevel(oldLevel);
        Path destination = Path.of("target/evidence/concurrent-histories.json");
        Files.createDirectories(destination.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.toFile(),
            Map.of("scope", "two service instances sharing deterministic in-memory persistence; not AWS",
                "schedules", RESULTS, "calibration", CALIBRATION,
                "checker_bound", "complete register histories of at most eight operations"));
    }

    @TestFactory Stream<DynamicTest> enumerateSchedulesControlsAndLagModels() {
        List<DynamicTest> tests = new ArrayList<>();
        for (List<String> schedule : SCHEDULES) {
            tests.add(DynamicTest.dynamicTest("two-instances/" + String.join("-", schedule), () -> scheduled(schedule)));
        }
        for (int firstActor : List.of(0, 1)) {
            tests.add(DynamicTest.dynamicTest("one-instance/serial-first-" + firstActor,
                () -> sequential("one-instance-serial-" + firstActor, 0, 2, firstActor, true)));
        }
        for (int lag : List.of(0, 1, 2)) for (int calls : List.of(2, 3)) {
            tests.add(DynamicTest.dynamicTest("one-instance/lag-" + lag + "/calls-" + calls,
                () -> sequential("one-instance-lag-" + lag + "-calls-" + calls, lag, calls, 0, lag == 0)));
        }
        return tests.stream();
    }

    static ShipmentRegistrationService service(InMemoryShipmentRepository repository, ManifestArchive archive) {
        return new ShipmentRegistrationService(repository, archive, new SimulationTariffClient(),
            new ChargeCalculator(), new ManifestMapper(), Clock.systemUTC());
    }

    static Operation invoke(int actor, ShipmentRegistrationService service, AtomicInteger clock) {
        int invocation = clock.incrementAndGet();
        String outcome;
        try { service.register(manifest(), raw()); outcome = "CREATED"; }
        catch (DuplicateManifestException expected) { outcome = "DUPLICATE"; }
        return new Operation(actor, invocation, clock.incrementAndGet(), MANIFEST, outcome);
    }

    void scheduled(List<String> schedule) throws Exception {
        Gates gates = new Gates();
        AtomicInteger clock = new AtomicInteger();
        ThreadLocal<Integer> actor = new ThreadLocal<>();
        var repository = new InMemoryShipmentRepository() {
            @Override public boolean manifestExists(String number) {
                int client = actor.get();
                gates.enter("C" + client);
                boolean found = super.manifestExists(number);
                gates.events.add("C" + client + "=" + found);
                if (found) gates.skip("P" + client);
                gates.finish("C" + client);
                return found;
            }
            @Override public void put(ShipmentItem item) {
                int client = actor.get();
                gates.enter("P" + client);
                super.put(item);
                gates.events.add("P" + client + "=COMMITTED");
                gates.finish("P" + client);
            }
        };
        ManifestArchive archive = new InMemoryManifestArchive();
        ShipmentRegistrationService[] instances = {service(repository, archive), service(repository, archive)};
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Operation>> futures = new ArrayList<>();
        try {
            for (int id = 0; id < 2; id++) {
                final int client = id;
                futures.add(pool.submit(() -> { actor.set(client); return invoke(client, instances[client], clock); }));
            }
            gates.awaitReady("C0");
            gates.awaitReady("C1");
            for (String step : schedule) gates.releaseAndAwait(step);
            List<Operation> history = List.of(futures.get(0).get(5, TimeUnit.SECONDS), futures.get(1).get(5, TimeUnit.SECONDS));
            boolean expected = schedule.get(1).startsWith("P");
            boolean accepted = witness(history).isPresent();
            assertThat(accepted).as(schedule.toString()).isEqualTo(expected);
            assertThat(repository.listByCarrier(CARRIER)).hasSize(expected ? 1 : 2);
            record("two-instances/" + String.join("-", schedule), history, accepted,
                repository.listByCarrier(CARRIER).size(), List.copyOf(gates.events));
        } finally {
            gates.releaseAll();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    void sequential(String name, int lag, int calls, int firstActor, boolean expected) {
        var repository = new InMemoryShipmentRepository() {
            int pending;
            @Override public void put(ShipmentItem item) { super.put(item); pending = lag; }
            @Override public boolean manifestExists(String manifest) {
                if (pending > 0) { pending--; return false; }
                return super.manifestExists(manifest);
            }
        };
        var service = service(repository, new InMemoryManifestArchive());
        AtomicInteger clock = new AtomicInteger();
        List<Operation> history = new ArrayList<>();
        for (int call = 0; call < calls; call++) history.add(invoke((call + firstActor) % 2, service, clock));
        boolean accepted = witness(history).isPresent();
        assertThat(accepted).isEqualTo(expected);
        assertThat(repository.listByCarrier(CARRIER)).hasSize(expected ? 1 : calls);
        record(name, history, accepted, repository.listByCarrier(CARRIER).size(), List.of());
    }

    static void record(String name, List<Operation> history, boolean accepted, int shipments, List<String> events) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("operations", history);
        result.put("linearizable", accepted);
        result.put("witness", witness(history).orElse(List.of()));
        result.put("persisted_shipments", shipments);
        result.put("events", events);
        result.put("finding", accepted ? "MODEL_ACCEPTS" : "UNIQUENESS_COUNTEREXAMPLE");
        RESULTS.add(result);
    }

    @Test void exhaustiveTwoCallTruthTableCalibratesTheCheckerIndependently() {
        for (boolean sameKey : List.of(false, true)) {
            for (String pattern : List.of("FIRST_BEFORE_SECOND", "SECOND_BEFORE_FIRST", "OVERLAP")) {
                for (String first : List.of("CREATED", "DUPLICATE")) {
                    for (String second : List.of("CREATED", "DUPLICATE")) {
                        int[] stamps = switch (pattern) {
                            case "FIRST_BEFORE_SECOND" -> new int[]{1, 2, 3, 4};
                            case "SECOND_BEFORE_FIRST" -> new int[]{3, 4, 1, 2};
                            default -> new int[]{1, 3, 2, 4};
                        };
                        List<Operation> history = List.of(new Operation(0, stamps[0], stamps[1], "A", first),
                            new Operation(1, stamps[2], stamps[3], sameKey ? "A" : "B", second));
                        // Closed-form truth table, not another state-space search.
                        boolean expected = sameKey
                            ? !first.equals(second) && (pattern.equals("OVERLAP")
                                || pattern.equals("FIRST_BEFORE_SECOND") && first.equals("CREATED")
                                || pattern.equals("SECOND_BEFORE_FIRST") && second.equals("CREATED"))
                            : first.equals("CREATED") && second.equals("CREATED");
                        boolean observed = witness(history).isPresent();
                        assertThat(observed).as("%s %s %s %s", sameKey, pattern, first, second).isEqualTo(expected);
                        CALIBRATION.add(Map.of("same_key", sameKey, "interval", pattern, "first", first,
                            "second", second, "expected", expected, "observed", observed));
                    }
                }
            }
        }
        assertThat(CALIBRATION).hasSize(24);
    }

    @Test void checkerRejectsMalformedIntervalsAndUnsupportedOutcomes() {
        assertThatThrownBy(() -> witness(List.of(new Operation(0, 2, 1, "A", "CREATED"))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> witness(List.of(new Operation(0, 1, 2, "A", "TIMEOUT"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    static Optional<List<Integer>> witness(List<Operation> history) {
        if (history.size() > 8) throw new IllegalArgumentException("Declared bound is eight complete operations");
        for (Operation operation : history) {
            if (operation.invocation() >= operation.response()
                || !Set.of("CREATED", "DUPLICATE").contains(operation.outcome()))
                throw new IllegalArgumentException("Complete valid intervals and known outcomes required");
        }
        return search(history, new boolean[history.size()], Set.of(), new ArrayList<>());
    }

    static Optional<List<Integer>> search(List<Operation> history, boolean[] used, Set<String> active, List<Integer> prefix) {
        if (prefix.size() == history.size()) return Optional.of(List.copyOf(prefix));
        for (int index = 0; index < history.size(); index++) {
            if (used[index]) continue;
            Operation next = history.get(index);
            boolean predecessorMissing = false;
            for (int other = 0; other < history.size(); other++) {
                if (!used[other] && history.get(other).response() < next.invocation()) predecessorMissing = true;
            }
            if (predecessorMissing) continue;
            String expected = active.contains(next.manifest()) ? "DUPLICATE" : "CREATED";
            if (!next.outcome().equals(expected)) continue;
            Set<String> after = new HashSet<>(active);
            after.add(next.manifest());
            used[index] = true;
            prefix.add(index);
            Optional<List<Integer>> result = search(history, used, after, prefix);
            prefix.remove(prefix.size() - 1);
            used[index] = false;
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    static class Gates {
        record Step(CountDownLatch ready, CountDownLatch allow, CountDownLatch done) { }
        final Map<String, Step> steps = new LinkedHashMap<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        Gates() {
            for (String name : List.of("C0", "P0", "C1", "P1"))
                steps.put(name, new Step(new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)));
        }
        void enter(String name) { steps.get(name).ready().countDown(); await(steps.get(name).allow(), name + " permit"); }
        void finish(String name) { steps.get(name).done().countDown(); }
        void skip(String name) {
            events.add(name + "=SKIPPED_DUPLICATE");
            steps.get(name).ready().countDown(); steps.get(name).done().countDown();
        }
        void awaitReady(String name) { await(steps.get(name).ready(), name + " ready"); }
        void releaseAndAwait(String name) {
            awaitReady(name); steps.get(name).allow().countDown(); await(steps.get(name).done(), name + " done");
        }
        void releaseAll() { steps.values().forEach(step -> step.allow().countDown()); }
        static void await(CountDownLatch latch, String stage) {
            try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Harness did not reach " + stage); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
    }
}
