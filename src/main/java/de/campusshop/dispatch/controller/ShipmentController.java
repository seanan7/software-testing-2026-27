package de.campusshop.dispatch.controller;

import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.service.ShipmentQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import jakarta.validation.constraints.Pattern;

/**
 * Read and delete endpoints for registered shipments.
 */
@RestController
public class ShipmentController {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final ShipmentQueryService queries;

    public ShipmentController(ShipmentQueryService queries) {
        this.queries = queries;
    }

    /** One shipment. The indexed shipment; 404 when unknown. */
    @GetMapping("/shipments/{shipmentRef}")
    public ShipmentSummary shipment(@PathVariable @Pattern(regexp = UUID_PATTERN) String shipmentRef) {
        return queries.find(shipmentRef);
    }

    /** Archived manifest. The manifest exactly as the partner sent it. */
    @GetMapping(value = "/shipments/{shipmentRef}/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] manifest(@PathVariable @Pattern(regexp = UUID_PATTERN) String shipmentRef) {
        return queries.manifest(shipmentRef);
    }

    /** Shipments of a carrier. Oldest dispatchDate first. */
    @GetMapping("/carriers/{carrierId}/shipments")
    public List<ShipmentSummary> carrierShipments(@PathVariable @Pattern(regexp = UUID_PATTERN) String carrierId) {
        return queries.listByCarrier(carrierId);
    }

    /** Delete a shipment. Removes the shipment; the archived manifest per the documented policy. */
    @DeleteMapping("/shipments/{shipmentRef}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Pattern(regexp = UUID_PATTERN) String shipmentRef) {
        queries.delete(shipmentRef);
    }
}
