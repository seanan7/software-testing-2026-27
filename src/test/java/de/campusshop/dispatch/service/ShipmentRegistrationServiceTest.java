package de.campusshop.dispatch.service;

import de.campusshop.dispatch.client.TariffClient;
import de.campusshop.dispatch.exception.DuplicateManifestException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.Rating;
import de.campusshop.dispatch.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShipmentRegistrationServiceTest {
    @Mock ShipmentRepository shipments;
    @Mock ManifestArchive archive;
    @Mock TariffClient tariffClient;
    ShipmentRegistrationService service;
    final Clock clock = Clock.fixed(Instant.parse("2026-11-20T09:15:00Z"), ZoneOffset.UTC);
    @BeforeEach void setUp() {
        service = new ShipmentRegistrationService(shipments, archive, tariffClient, new ChargeCalculator(), new ManifestMapper(), clock);
    }
    void ratedTariffs() {
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.of(STANDARD));
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.of(EXPRESS));
    }
    @Test void registersArchivesAndIndexesInTheRequiredOrder() {
        ratedTariffs();
        byte[] raw = raw();
        var record = service.register(manifest(), raw);
        assertThat(UUID.fromString(record.shipmentRef()).toString()).isEqualTo(record.shipmentRef());
        assertThat(record.registeredAt()).isEqualTo(clock.instant());
        assertThat(record.totalCharge()).isEqualTo(dec("16.99"));
        assertThat(record.parcelCount()).isEqualTo(2);
        assertThat(record.parcels()).extracting(p -> p.charge()).containsExactly(dec("8.25"), dec("8.74"));
        assertThat(record.parcels()).extracting(p -> p.parcelNo()).containsExactly(1, 2);
        assertThat(record.parcels()).allMatch(p -> p.rating() == Rating.RATED);
        var saved = ArgumentCaptor.forClass(ShipmentItem.class);
        InOrder order = inOrder(shipments, archive, tariffClient);
        order.verify(shipments).manifestExists(MANIFEST);
        order.verify(archive).archive(eq(record.manifestKey()), same(raw));
        order.verify(tariffClient).findTariff("TRF-1");
        order.verify(tariffClient).findTariff("TRF-2");
        order.verify(shipments).put(saved.capture());
        assertThat(saved.getValue()).usingRecursiveComparison().isEqualTo(new ManifestMapper().toSummary(saved.getValue()));
        assertThat(saved.getValue().shipmentRef()).isEqualTo(record.shipmentRef());
        assertThat(saved.getValue().manifestKey()).isEqualTo("manifests/" + CARRIER + "/" + record.shipmentRef() + ".json");
        verify(archive, never()).remove(anyString());
    }
    @Test void unknownTariffLeavesTheParcelUnrated() {
        when(tariffClient.findTariff("TRF-1")).thenReturn(Optional.of(STANDARD));
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.empty());
        var record = service.register(manifest(), raw());
        assertThat(record.parcelCount()).isEqualTo(2);
        assertThat(record.totalCharge()).isEqualTo(dec("8.25"));
        var parcel = record.parcels().get(1);
        assertThat(parcel.rating()).isEqualTo(Rating.UNRATED);
        assertThat(parcel.parcelNo()).isEqualTo(2);
        assertThat(parcel.tariffCode()).isEqualTo("TRF-2");
        assertThat(parcel.weightKg()).isEqualTo(dec("0.8"));
        assertThat(parcel.serviceName()).isNull();
        assertThat(parcel.baseFee()).isNull();
        assertThat(parcel.pricePerKg()).isNull();
        assertThat(parcel.fuelSurchargeRate()).isNull();
        assertThat(parcel.charge()).isNull();
        verify(shipments).put(any());
    }
    @Test void tariffTransportFailureDoesNotAbortRegistration() {
        when(tariffClient.findTariff("TRF-1")).thenThrow(new IllegalStateException("connection refused"));
        when(tariffClient.findTariff("TRF-2")).thenReturn(Optional.of(EXPRESS));
        var record = service.register(manifest(), raw());
        assertThat(record.parcels().getFirst().rating()).isEqualTo(Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualTo(dec("8.74"));
        verify(shipments).put(any());
    }
    @Test void entirelyUnratedShipmentHasZeroTotal() {
        var record = service.register(manifest(), raw());
        assertThat(record.parcelCount()).isEqualTo(2);
        assertThat(record.parcels()).allMatch(p -> p.rating() == Rating.UNRATED);
        assertThat(record.totalCharge()).isEqualTo(dec("0.00"));
        verify(shipments).put(any());
    }
    @Test void duplicateIsRejectedBeforeAnythingIsWritten() {
        when(shipments.manifestExists(MANIFEST)).thenReturn(true);
        assertThatThrownBy(() -> service.register(manifest(), raw())).isInstanceOf(DuplicateManifestException.class);
        verifyNoInteractions(archive, tariffClient);
        verify(shipments, never()).put(any());
    }
    @Test void failedIndexWriteRemovesTheExactArchivedManifest() {
        ratedTariffs();
        var failure = new IllegalStateException("DynamoDB unavailable");
        doThrow(failure).when(shipments).put(any());
        assertThatThrownBy(() -> service.register(manifest(), raw())).isSameAs(failure);
        var key = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(archive, shipments);
        order.verify(shipments).manifestExists(MANIFEST);
        order.verify(archive).archive(key.capture(), any());
        order.verify(shipments).put(any());
        order.verify(archive).remove(key.getValue());
    }
    @Test void archiveFailureStopsPricingAndIndexing() {
        doThrow(new IllegalStateException("S3 unavailable")).when(archive).archive(anyString(), any());
        assertThatThrownBy(() -> service.register(manifest(), raw())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tariffClient);
        verify(shipments, never()).put(any());
        verify(archive, never()).remove(anyString());
    }
    @Test void duplicateLookupFailureDoesNotWriteAnything() {
        when(shipments.manifestExists(MANIFEST)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.register(manifest(), raw())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(archive, tariffClient);
        verify(shipments, never()).put(any());
    }
    @Test void cleanupFailurePreservesTheOriginalCause() {
        var indexFailure = new IllegalStateException("index failed");
        var cleanupFailure = new IllegalStateException("delete failed");
        doThrow(indexFailure).when(shipments).put(any());
        doThrow(cleanupFailure).when(archive).remove(anyString());
        assertThatThrownBy(() -> service.register(manifest(), raw())).isSameAs(indexFailure)
                .satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(cleanupFailure));
    }
}
