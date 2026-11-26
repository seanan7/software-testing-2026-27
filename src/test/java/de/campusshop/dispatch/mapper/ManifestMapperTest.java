package de.campusshop.dispatch.mapper;

import de.campusshop.dispatch.dto.TariffResponse;
import de.campusshop.dispatch.model.RatedParcel;
import de.campusshop.dispatch.model.Rating;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import java.util.List;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ManifestMapperTest {
    private final ManifestMapper mapper = new ManifestMapper();
    @ParameterizedTest @CsvSource({"0,1,0.1", "1,0,0.1", "1,1,0", "0,0,0"})
    void zeroTariffValuesAreValid(String base, String price, String fuel) {
        var response = new TariffResponse("TRF-1", "Standard", dec(base), dec(price), dec(fuel), 2);
        var tariff = mapper.toTariff(response);
        assertThat(tariff.baseFee()).isEqualTo(response.baseFee());
        assertThat(tariff.pricePerKg()).isEqualTo(response.pricePerKg());
        assertThat(tariff.fuelSurchargeRate()).isEqualTo(response.fuelSurchargeRate());
    }
    static Stream<TariffResponse> invalidTariffs() {
        return Stream.of(
            new TariffResponse(null, "Standard", dec("1"), dec("1"), dec("0"), 2),
            new TariffResponse(" ", "Standard", dec("1"), dec("1"), dec("0"), 2),
            new TariffResponse("TRF-1", null, dec("1"), dec("1"), dec("0"), 2),
            new TariffResponse("TRF-1", " ", dec("1"), dec("1"), dec("0"), 2),
            new TariffResponse("TRF-1", "Standard", dec("1"), null, dec("0"), 2),
            new TariffResponse("TRF-1", "Standard", dec("1"), dec("1"), null, 2),
            new TariffResponse("TRF-1", "Standard", dec("-1"), dec("1"), dec("0"), 2),
            new TariffResponse("TRF-1", "Standard", dec("1"), dec("-1"), dec("0"), 2),
            new TariffResponse("TRF-1", "Standard", dec("1"), dec("1"), dec("-1"), 2));
    }
    @ParameterizedTest @MethodSource("invalidTariffs")
    void rejectsInvalidFieldsIndividually(TariffResponse response) {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toTariff(response));
    }
    @Test void mapsExternalTariffToDomainModel() {
        assertThat(mapper.toTariff(new TariffResponse("TRF-1", "Standard", dec("4.50"), dec("1.20"), dec("0.10"), 2)))
                .isEqualTo(STANDARD);
    }
    @Test void rejectsIncompleteTariffs() {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toTariff(null));
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toTariff(
                new TariffResponse("TRF-1", "Standard", null, dec("1"), dec("0"), 2)));
    }
    @Test void mapsEveryRatedParcelField() {
        var parcel = mapper.toParcelCharge(new RatedParcel(1, "TRF-1", dec("2.5"), STANDARD, dec("8.25")));
        assertThat(parcel.parcelNo()).isEqualTo(1);
        assertThat(parcel.tariffCode()).isEqualTo("TRF-1");
        assertThat(parcel.serviceName()).isEqualTo("Standard");
        assertThat(parcel.weightKg()).isEqualTo(dec("2.5"));
        assertThat(parcel.baseFee()).isEqualTo(STANDARD.baseFee());
        assertThat(parcel.pricePerKg()).isEqualTo(STANDARD.pricePerKg());
        assertThat(parcel.fuelSurchargeRate()).isEqualTo(STANDARD.fuelSurchargeRate());
        assertThat(parcel.charge()).isEqualTo(dec("8.25"));
        assertThat(parcel.rating()).isEqualTo(Rating.RATED);
    }
    @Test void mapsUnratedParcelWithoutInventingCharges() {
        var parcel = mapper.toParcelCharge(new RatedParcel(2, "UNKNOWN", dec("0.8"), null, null));
        assertThat(parcel.parcelNo()).isEqualTo(2);
        assertThat(parcel.tariffCode()).isEqualTo("UNKNOWN");
        assertThat(parcel.weightKg()).isEqualTo(dec("0.8"));
        assertThat(parcel.rating()).isEqualTo(Rating.UNRATED);
        assertThat(parcel.serviceName()).isNull();
        assertThat(parcel.baseFee()).isNull();
        assertThat(parcel.pricePerKg()).isNull();
        assertThat(parcel.fuelSurchargeRate()).isNull();
        assertThat(parcel.charge()).isNull();
    }
    @Test void mapsEveryStoredAttributeToRecordAndSummary() {
        var parcels = List.of(new RatedParcel(1, "TRF-1", dec("2.5"), STANDARD, dec("8.25")),
                new RatedParcel(2, "TRF-2", dec("0.8"), EXPRESS, dec("8.74")));
        var record = mapper.toRecord(item(), parcels);
        assertThat(record).usingRecursiveComparison().ignoringFields("parcels").isEqualTo(item());
        assertThat(mapper.toSummary(item())).usingRecursiveComparison().isEqualTo(item());
        assertThat(record.parcels()).containsExactly(mapper.toParcelCharge(parcels.get(0)), mapper.toParcelCharge(parcels.get(1)));
    }
}
