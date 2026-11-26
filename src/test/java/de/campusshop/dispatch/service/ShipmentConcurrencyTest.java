package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.SimulationTariffClient;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.concurrent.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ShipmentConcurrencyTest {
    @Test void simultaneousDuplicateRequestsCreateExactlyOneShipment() throws Exception {
        CountDownLatch bothChecks = new CountDownLatch(2);
        var repository = new InMemoryShipmentRepository() {
            @Override public boolean manifestExists(String number) {
                boolean snapshot = super.manifestExists(number);
                bothChecks.countDown();
                try { bothChecks.await(200, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return snapshot;
            }
        };
        var service = new ShipmentRegistrationService(repository, new InMemoryManifestArchive(),
                new SimulationTariffClient(), new ChargeCalculator(), new ManifestMapper(), Clock.systemUTC());
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> operation = () -> {
            start.await();
            try { service.register(manifest(), raw()); return "CREATED"; }
            catch (DuplicateManifestException expected) { return "DUPLICATE"; }
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(operation), second = pool.submit(operation);
            start.countDown();
            assertThat(new String[]{first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder("CREATED", "DUPLICATE");
            assertThat(repository.listByCarrier(CARRIER)).hasSize(1);
        }
    }
}
