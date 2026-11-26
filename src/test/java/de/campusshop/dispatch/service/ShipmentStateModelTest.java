package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.SimulationTariffClient;
import de.campusshop.dispatch.dto.*;
import de.campusshop.dispatch.exception.*;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ShipmentStateModelTest {
    record Expected(String manifestNo, String ref, String key, byte[] raw, String total) { }

    @Test void hundredGeneratedFortyOperationSequencesMatchTheIndependentActiveStateModel() {
        Random random = new Random(20261126L);
        for (int sequence = 0; sequence < 100; sequence++) {
            var repository = new InMemoryShipmentRepository();
            var archive = new InMemoryManifestArchive();
            var registration = new ShipmentRegistrationService(repository, archive, new SimulationTariffClient(),
                    new ChargeCalculator(), new ManifestMapper(), Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC));
            var queries = new ShipmentQueryService(repository, archive, new ManifestMapper());
            Map<String, Expected> expected = new LinkedHashMap<>();
            for (int step = 0; step < 40; step++) {
                int operation = expected.isEmpty() ? 0 : random.nextInt(4);
                if (operation == 0) {
                    String number = new UUID(random.nextLong(), random.nextLong()).toString();
                    int mode = random.nextInt(3);
                    String first = mode == 2 ? "TRF-9999" : "TRF-1", second = mode == 0 ? "TRF-2" : "TRF-9999";
                    var manifest = new DispatchManifest(number, manifest().carrier(), LocalDate.of(2026, 11, 20), null,
                            List.of(new ParcelDto(first, dec("2.5")), new ParcelDto(second, dec("0.8"))));
                    byte[] raw = new String(raw(number, CARRIER), java.nio.charset.StandardCharsets.UTF_8)
                            .replace("TRF-1", first).replace("TRF-2", second).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var record = registration.register(manifest, raw);
                    String total = mode == 0 ? "16.99" : mode == 1 ? "8.25" : "0.00";
                    assertThat(record.totalCharge()).isEqualByComparingTo(total);
                    assertThat(record.parcelCount()).isEqualTo(2);
                    expected.put(number, new Expected(number, record.shipmentRef(), record.manifestKey(), raw.clone(), total));
                } else {
                    var active = new ArrayList<>(expected.values());
                    Expected sample = active.get(random.nextInt(active.size()));
                    switch (operation) {
                        case 1 -> {
                            assertThat(queries.find(sample.ref()).totalCharge()).isEqualByComparingTo(sample.total());
                            assertThat(queries.manifest(sample.ref())).isEqualTo(sample.raw());
                        }
                        case 2 -> {
                            var duplicate = new DispatchManifest(sample.manifestNo(), manifest().carrier(), manifest().dispatchDate(), null, manifest().parcels());
                            assertThatThrownBy(() -> registration.register(duplicate, sample.raw())).isInstanceOf(DuplicateManifestException.class);
                        }
                        case 3 -> {
                            queries.delete(sample.ref());
                            expected.remove(sample.manifestNo());
                            assertThat(archive.fetch(sample.key())).isEmpty();
                            assertThatThrownBy(() -> queries.find(sample.ref())).isInstanceOf(ShipmentNotFoundException.class);
                        }
                        default -> throw new AssertionError();
                    }
                }
                assertThat(queries.listByCarrier(CARRIER)).hasSize(expected.size());
                assertThat(repository.listByCarrier(CARRIER)).extracting(item -> item.manifestNo())
                        .containsExactlyInAnyOrderElementsOf(expected.keySet());
            }
        }
    }
}
