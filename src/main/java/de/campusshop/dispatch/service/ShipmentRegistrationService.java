package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.TariffClient;
import de.campusshop.dispatch.dto.DispatchManifest;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.dto.ParcelDto;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.model.RatedParcel;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import de.campusshop.dispatch.model.Tariff;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import de.campusshop.dispatch.repository.ManifestKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers a validated manifest. Dependencies are wired for you through constructor injection.
 *
 * <p>Duplicate check, archive, per-parcel pricing, then index. A failed index write is compensated
 * by removing the evidence written by this registration.
 */
@Service
public class ShipmentRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentRegistrationService.class);

    private final ShipmentRepository shipments;
    private final ManifestArchive archive;
    private final TariffClient tariffClient;
    private final ChargeCalculator calculator;
    private final ManifestMapper mapper;
    private final Clock clock;

    public ShipmentRegistrationService(ShipmentRepository shipments, ManifestArchive archive, TariffClient tariffClient,
                                       ChargeCalculator calculator, ManifestMapper mapper, Clock clock) {
        this.shipments = shipments;
        this.archive = archive;
        this.tariffClient = tariffClient;
        this.calculator = calculator;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param manifest    the parsed and validated manifest
     * @param rawManifest the request body exactly as received; this is what goes to S3
     */
    // Serialises the check/write sequence within this service instance. Distributed/GSI
    // consistency still requires a stronger persistence design than this single-process lock.
    public synchronized ShipmentRecord register(DispatchManifest manifest, byte[] rawManifest) {
        boolean duplicate;
        try {
            duplicate = shipments.manifestExists(manifest.manifestNo());
        } catch (RuntimeException ex) {
            log.error("Duplicate lookup failed for manifest {}", manifest.manifestNo(), ex);
            throw ex;
        }
        if (duplicate) {
            throw new DuplicateManifestException(manifest.manifestNo());
        }
        String shipmentRef = UUID.randomUUID().toString();
        String key = ManifestKeys.keyFor(manifest.carrier().carrierId(), shipmentRef);
        try {
            archive.archive(key, rawManifest);
        } catch (RuntimeException ex) {
            log.error("Archive failed for shipment {} manifest {}", shipmentRef, manifest.manifestNo(), ex);
            throw ex;
        }
        try {
            List<RatedParcel> parcels = new ArrayList<>();
            BigDecimal total = new BigDecimal("0.00");
            for (ParcelDto parcel : manifest.parcels()) {
                Optional<Tariff> tariff;
                try {
                    tariff = tariffClient.findTariff(parcel.tariffCode());
                } catch (RuntimeException ex) {
                    log.warn("Tariff lookup failed for shipment {} manifest {} code {}",
                            shipmentRef, manifest.manifestNo(), parcel.tariffCode());
                    tariff = Optional.empty();
                }
                BigDecimal charge = tariff.map(t -> calculator.charge(t, parcel.weightKg())).orElse(null);
                parcels.add(new RatedParcel(parcels.size() + 1, parcel.tariffCode(), parcel.weightKg(),
                        tariff.orElse(null), charge));
                if (charge != null) {
                    total = total.add(charge);
                } else {
                    log.warn("UNRATED parcel {} code {} for shipment {} manifest {}", parcels.size(),
                            parcel.tariffCode(), shipmentRef, manifest.manifestNo());
                }
            }
            ShipmentItem item = new ShipmentItem(shipmentRef, manifest.manifestNo(), manifest.carrier().carrierId(),
                    manifest.dispatchDate(), clock.instant(), ShipmentState.REGISTERED,
                    parcels.size(), total, key);
            ShipmentRecord record = mapper.toRecord(item, parcels);
            shipments.put(item);
            log.info("Registered shipment {} manifest {} with {} parcels and total {}",
                    shipmentRef, manifest.manifestNo(), parcels.size(), total);
            return record;
        } catch (RuntimeException ex) {
            try {
                archive.remove(key);
                log.warn("Removed archived manifest after failed registration of shipment {} manifest {}",
                        shipmentRef, manifest.manifestNo());
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
                log.error("Archive clean-up failed for shipment {} manifest {} key {}",
                        shipmentRef, manifest.manifestNo(), key, cleanupFailure);
            }
            log.error("Registration failed for shipment {} manifest {}", shipmentRef, manifest.manifestNo(), ex);
            throw ex;
        }
    }
}
