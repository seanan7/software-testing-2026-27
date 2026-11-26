package de.campusshop.dispatch.service;

import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.repository.ManifestArchive;
import de.campusshop.dispatch.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read and delete operations on registered shipments. Unknown shipments end in
 * {@code ShipmentNotFoundException} (404).
 *
 * <p>Deletion policy: remove both the archived manifest and the shipment item. A successful
 * deletion returns 204 No Content. Subsequent reads and deletes return 404.
 */
@Service
public class ShipmentQueryService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentQueryService.class);

    private final ShipmentRepository shipments;
    private final ManifestArchive archive;
    private final ManifestMapper mapper;

    public ShipmentQueryService(ShipmentRepository shipments, ManifestArchive archive, ManifestMapper mapper) {
        this.shipments = shipments;
        this.archive = archive;
        this.mapper = mapper;
    }

    public ShipmentSummary find(String shipmentRef) {
        return mapper.toSummary(requireShipment(shipmentRef));
    }

    /** Oldest dispatchDate first. */
    public List<ShipmentSummary> listByCarrier(String carrierId) {
        return shipments.listByCarrier(carrierId).stream().map(mapper::toSummary).toList();
    }

    /** The archived manifest bytes. */
    public byte[] manifest(String shipmentRef) {
        ShipmentItem item = requireShipment(shipmentRef);
        return archive.fetch(item.manifestKey()).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
    }

    public void delete(String shipmentRef) {
        ShipmentItem item = requireShipment(shipmentRef);
        try {
            archive.remove(item.manifestKey());
            shipments.remove(shipmentRef);
            log.info("Deleted shipment {} manifest {} and archived evidence", shipmentRef, item.manifestNo());
        } catch (RuntimeException ex) {
            log.error("Deletion failed for shipment {} manifest {}", shipmentRef, item.manifestNo(), ex);
            throw ex;
        }
    }

    private ShipmentItem requireShipment(String shipmentRef) {
        return shipments.get(shipmentRef).orElseThrow(() -> new ShipmentNotFoundException(shipmentRef));
    }
}
