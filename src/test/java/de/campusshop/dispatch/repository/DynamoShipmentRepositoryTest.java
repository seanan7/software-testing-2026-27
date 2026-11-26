package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import de.campusshop.dispatch.model.ShipmentItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.LocalDate;
import java.util.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DynamoShipmentRepositoryTest {
    @Mock DynamoDbClient dynamo;
    DynamoShipmentRepository repository;
    @BeforeEach void setUp() {
        repository = new DynamoShipmentRepository(dynamo, new DispatchProperties(
                new DispatchProperties.Archive("custom-bucket"), new DispatchProperties.Shipments("custom-table"), null));
    }
    Map<String, AttributeValue> stored(ShipmentItem item) {
        repository.put(item);
        var request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo, atLeastOnce()).putItem(request.capture());
        return request.getValue().item();
    }
    @Test void writesExactlyTheNineAttributesWithNativeNumbers() {
        var attributes = stored(item());
        assertThat(attributes).hasSize(9);
        assertThat(attributes.get("parcelCount").n()).isEqualTo("2");
        assertThat(attributes.get("totalCharge").n()).isEqualTo("16.99");
        assertThat(attributes.get("state").s()).isEqualTo("REGISTERED");
        assertThat(attributes.get("manifestKey").s()).isEqualTo(item().manifestKey());
        var request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(request.capture());
        assertThat(request.getValue().tableName()).isEqualTo("custom-table");
    }
    @Test void moneyIsNotWrittenInScientificNotation() {
        var i = item();
        var attributes = stored(new ShipmentItem(i.shipmentRef(), i.manifestNo(), i.carrierId(), i.dispatchDate(),
                i.registeredAt(), i.state(), 1, dec("1E+9"), i.manifestKey()));
        assertThat(attributes.get("totalCharge").n()).isEqualTo("1000000000");
    }
    @Test void getsByPartitionKeyAndReconstructsEveryField() {
        var attributes = stored(item());
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(attributes).build());
        assertThat(repository.get(REF)).contains(item());
        var request = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamo).getItem(request.capture());
        assertThat(request.getValue().tableName()).isEqualTo("custom-table");
        assertThat(request.getValue().key()).containsExactlyEntriesOf(Map.of("shipmentRef", s(REF)));
        assertThat(request.getValue().consistentRead()).isTrue();
    }
    @Test void missingItemIsEmpty() {
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertThat(repository.get(REF)).isEmpty();
    }
    @Test void duplicateCheckQueriesManifestIndexRatherThanScanning() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().count(1).build(), QueryResponse.builder().count(0).build());
        assertThat(repository.manifestExists(MANIFEST)).isTrue();
        assertThat(repository.manifestExists(MANIFEST)).isFalse();
        var request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo, times(2)).query(request.capture());
        assertThat(request.getValue().indexName()).isEqualTo("manifest-index");
        assertThat(request.getValue().keyConditionExpression()).isEqualTo("manifestNo = :m");
        assertThat(request.getValue().expressionAttributeValues()).containsEntry(":m", s(MANIFEST));
        assertThat(request.getValue().select()).isEqualTo(Select.COUNT);
        verify(dynamo, never()).scan(any(ScanRequest.class));
    }
    @Test void carrierQueryFollowsEveryPageInAscendingDateOrder() {
        var first = item();
        var later = new ShipmentItem("aaaaaaaa-1111-4111-8111-111111111111", first.manifestNo(), CARRIER,
                LocalDate.parse("2026-12-01"), first.registeredAt(), first.state(), 2, first.totalCharge(), first.manifestKey());
        var firstMap = stored(first);
        var laterMap = stored(later);
        Map<String, AttributeValue> continuation = Map.of("shipmentRef", s(REF));
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(firstMap).lastEvaluatedKey(continuation).build(),
                QueryResponse.builder().items(laterMap).build());
        assertThat(repository.listByCarrier(CARRIER)).containsExactly(first, later);
        var request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo, times(2)).query(request.capture());
        for (var query : request.getAllValues()) {
            assertThat(query.tableName()).isEqualTo("custom-table");
            assertThat(query.indexName()).isEqualTo("carrier-dispatch-index");
            assertThat(query.scanIndexForward()).isTrue();
            assertThat(query.keyConditionExpression()).isEqualTo("carrierId = :c");
            assertThat(query.expressionAttributeValues()).containsEntry(":c", s(CARRIER));
        }
        assertThat(request.getAllValues().getFirst().hasExclusiveStartKey()).isFalse();
        assertThat(request.getAllValues().get(1).exclusiveStartKey()).isEqualTo(continuation);
        verify(dynamo, never()).scan(any(ScanRequest.class));
    }
    @Test void emptyCarrierQueryReturnsAnEmptyList() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().build());
        assertThat(repository.listByCarrier(CARRIER)).isEmpty();
    }
    @Test void removesByPartitionKey() {
        repository.remove(REF);
        var request = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamo).deleteItem(request.capture());
        assertThat(request.getValue().tableName()).isEqualTo("custom-table");
        assertThat(request.getValue().key()).containsExactlyEntriesOf(Map.of("shipmentRef", s(REF)));
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
}
