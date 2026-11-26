package de.campusshop.dispatch;

import de.campusshop.dispatch.dto.*;
import de.campusshop.dispatch.model.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TestFixtures {
    public static final String CARRIER = "9c8b7a65-4d3e-4f21-9a0b-1c2d3e4f5a6b";
    public static final String MANIFEST = "4b1d2c3e-5f60-4a71-8b92-a3b4c5d6e7f8";
    public static final String REF = "1f2e3d4c-5b6a-4789-8abc-def012345678";
    public static final Tariff STANDARD = new Tariff("TRF-1", "Standard", dec("4.50"), dec("1.20"), dec("0.10"));
    public static final Tariff EXPRESS = new Tariff("TRF-2", "Express", dec("6.00"), dec("2.00"), dec("0.15"));
    private TestFixtures() { }
    public static BigDecimal dec(String value) { return new BigDecimal(value); }
    public static DispatchManifest manifest() {
        return new DispatchManifest(MANIFEST, new CarrierDto(CARRIER, "Campus Couriers", "ops@example.org"),
                LocalDate.parse("2026-11-20"), "EDI-01", List.of(
                new ParcelDto("TRF-1", dec("2.5")), new ParcelDto("TRF-2", dec("0.8"))));
    }
    public static ShipmentItem item() {
        return new ShipmentItem(REF, MANIFEST, CARRIER, LocalDate.parse("2026-11-20"),
                Instant.parse("2026-11-20T09:15:00Z"), ShipmentState.REGISTERED, 2, dec("16.99"),
                "manifests/" + CARRIER + "/" + REF + ".json");
    }
    public static byte[] raw(String manifestNo, String carrierId) {
        return ("""
                {
                  "manifestNo": "%s",
                  "carrier": {"carrierId": "%s", "name": "Campus Couriers", "contactEmail": "ops@example.org"},
                  "dispatchDate": "2026-11-20",
                  "originWarehouse": "EDI-01",
                  "parcels": [{"tariffCode":"TRF-1","weightKg":2.5},{"tariffCode":"TRF-2","weightKg":0.8}],
                  "carrierInternalNotes": "Keep exactly: café / 原始证据",
                  "extra": {"nested": [true, null, 1.2300]}
                }
                """).formatted(manifestNo, carrierId).getBytes(StandardCharsets.UTF_8);
    }
    public static byte[] raw() { return raw(MANIFEST, CARRIER); }
}
