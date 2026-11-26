package de.campusshop.dispatch.mapper;

import de.campusshop.dispatch.dto.ParcelCharge;
import de.campusshop.dispatch.dto.ShipmentRecord;
import de.campusshop.dispatch.dto.ShipmentSummary;
import de.campusshop.dispatch.dto.TariffResponse;
import de.campusshop.dispatch.model.RatedParcel;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.Tariff;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts between the external tariff JSON, the internal model and the response DTOs.
 */
@Component
public class ManifestMapper {

    /** External tariff JSON to the internal {@link Tariff}. */
    public Tariff toTariff(TariffResponse response) {
        if (response == null || response.tariffCode() == null || response.tariffCode().isBlank()
                || response.serviceName() == null || response.serviceName().isBlank()
                || response.baseFee() == null || response.pricePerKg() == null
                || response.fuelSurchargeRate() == null || response.baseFee().signum() < 0
                || response.pricePerKg().signum() < 0 || response.fuelSurchargeRate().signum() < 0) {
            throw new IllegalArgumentException("Tariff response is incomplete or invalid");
        }
        return new Tariff(response.tariffCode(), response.serviceName(), response.baseFee(),
                response.pricePerKg(), response.fuelSurchargeRate());
    }

    /** One priced parcel. For an UNRATED parcel all tariff fields and the charge are null. */
    public ParcelCharge toParcelCharge(RatedParcel parcel) {
        Tariff tariff = parcel.tariff();
        return new ParcelCharge(parcel.parcelNo(), parcel.tariffCode(),
                tariff == null ? null : tariff.serviceName(), parcel.weightKg(),
                tariff == null ? null : tariff.baseFee(), tariff == null ? null : tariff.pricePerKg(),
                tariff == null ? null : tariff.fuelSurchargeRate(),
                tariff == null ? null : parcel.charge(), parcel.rating());
    }

    /** The full registration response: the stored item plus every priced parcel. */
    public ShipmentRecord toRecord(ShipmentItem item, List<RatedParcel> parcels) {
        return new ShipmentRecord(item.shipmentRef(), item.manifestNo(), item.carrierId(),
                item.dispatchDate(), item.registeredAt(), item.state(), item.parcelCount(),
                item.totalCharge(), item.manifestKey(), parcels.stream().map(this::toParcelCharge).toList());
    }

    /** The stored item as returned by the read endpoints (no parcels). */
    public ShipmentSummary toSummary(ShipmentItem item) {
        return new ShipmentSummary(item.shipmentRef(), item.manifestNo(), item.carrierId(),
                item.dispatchDate(), item.registeredAt(), item.state(), item.parcelCount(),
                item.totalCharge(), item.manifestKey());
    }
}
