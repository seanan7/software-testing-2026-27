package de.campusshop.dispatch.service;

import de.campusshop.dispatch.exception.ShipmentNotFoundException;
import de.campusshop.dispatch.mapper.ManifestMapper;
import de.campusshop.dispatch.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShipmentQueryServiceTest {
    @Mock ShipmentRepository shipments;
    @Mock ManifestArchive archive;
    ShipmentQueryService service;
    @BeforeEach void setUp() { service = new ShipmentQueryService(shipments, archive, new ManifestMapper()); }
    @Test void findsStoredSummary() {
        when(shipments.get(REF)).thenReturn(Optional.of(item()));
        assertThat(service.find(REF)).usingRecursiveComparison().isEqualTo(item());
        verifyNoInteractions(archive);
    }
    @Test void retrievesOriginalBytes() {
        when(shipments.get(REF)).thenReturn(Optional.of(item()));
        when(archive.fetch(item().manifestKey())).thenReturn(Optional.of(raw()));
        assertThat(service.manifest(REF)).isEqualTo(raw());
    }
    @Test void missingArchiveReturns404() {
        when(shipments.get(REF)).thenReturn(Optional.of(item()));
        assertThatThrownBy(() -> service.manifest(REF)).isInstanceOf(ShipmentNotFoundException.class);
    }
    @Test void unknownShipmentsAre404WithoutArchiveAccess() {
        assertThatThrownBy(() -> service.find(REF)).isInstanceOf(ShipmentNotFoundException.class);
        assertThatThrownBy(() -> service.manifest(REF)).isInstanceOf(ShipmentNotFoundException.class);
        assertThatThrownBy(() -> service.delete(REF)).isInstanceOf(ShipmentNotFoundException.class);
        verifyNoInteractions(archive);
        verify(shipments, never()).remove(anyString());
    }
    @Test void carrierListingUsesRepositoryOrder() {
        when(shipments.listByCarrier(CARRIER)).thenReturn(List.of(item()));
        assertThat(service.listByCarrier(CARRIER)).containsExactly(new ManifestMapper().toSummary(item()));
        verifyNoInteractions(archive);
    }
    @Test void deletionRemovesBothStores() {
        when(shipments.get(REF)).thenReturn(Optional.of(item()));
        service.delete(REF);
        var order = inOrder(archive, shipments);
        order.verify(shipments).get(REF);
        order.verify(archive).remove(item().manifestKey());
        order.verify(shipments).remove(REF);
    }
    @Test void archiveDeleteFailureIsNotReportedAsSuccess() {
        when(shipments.get(REF)).thenReturn(Optional.of(item()));
        doThrow(new IllegalStateException("S3 unavailable")).when(archive).remove(item().manifestKey());
        assertThatThrownBy(() -> service.delete(REF)).isInstanceOf(IllegalStateException.class);
        verify(shipments, never()).remove(anyString());
    }
}
