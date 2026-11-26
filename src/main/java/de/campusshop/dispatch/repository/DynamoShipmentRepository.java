package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import de.campusshop.dispatch.model.ShipmentItem;
import de.campusshop.dispatch.model.ShipmentState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation of {@link ShipmentRepository}. The table and both GSIs are pre-created by
 * the environment; never create them here. Use {@code GetItem} and {@code Query} only — no
 * {@code Scan}.
 *
 * <ul>
 *   <li>partition key {@code shipmentRef};</li>
 *   <li>{@code manifest-index}: partition key {@code manifestNo} (duplicate check);</li>
 *   <li>{@code carrier-dispatch-index}: partition key {@code carrierId}, sort key {@code dispatchDate}.</li>
 * </ul>
 */
@Repository
@Profile("!inmemory")
public class DynamoShipmentRepository implements ShipmentRepository {

    static final String MANIFEST_INDEX = "manifest-index";
    static final String CARRIER_DISPATCH_INDEX = "carrier-dispatch-index";

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoShipmentRepository(DynamoDbClient dynamo, DispatchProperties properties) {
        this.dynamo = dynamo;
        this.table = properties.shipments().table();
    }

    @Override
    public void put(ShipmentItem item) {
        Map<String, AttributeValue> attributes = Map.of(
                "shipmentRef", string(item.shipmentRef()),
                "manifestNo", string(item.manifestNo()),
                "carrierId", string(item.carrierId()),
                "dispatchDate", string(item.dispatchDate().toString()),
                "registeredAt", string(item.registeredAt().toString()),
                "state", string(item.state().name()),
                "parcelCount", AttributeValue.builder().n(Integer.toString(item.parcelCount())).build(),
                "totalCharge", AttributeValue.builder().n(item.totalCharge().toPlainString()).build(),
                "manifestKey", string(item.manifestKey()));
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(attributes).build());
    }

    @Override
    public Optional<ShipmentItem> get(String shipmentRef) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("shipmentRef", string(shipmentRef))).consistentRead(true).build());
        return response.hasItem() ? Optional.of(toShipment(response.item())) : Optional.empty();
    }

    @Override
    public boolean manifestExists(String manifestNo) {
        return dynamo.query(QueryRequest.builder().tableName(table).indexName(MANIFEST_INDEX)
                .keyConditionExpression("manifestNo = :m")
                .expressionAttributeValues(Map.of(":m", string(manifestNo)))
                .select(Select.COUNT).limit(1).build()).count() > 0;
    }

    @Override
    public List<ShipmentItem> listByCarrier(String carrierId) {
        List<ShipmentItem> items = new ArrayList<>();
        Map<String, AttributeValue> lastKey = Map.of();
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(table)
                    .indexName(CARRIER_DISPATCH_INDEX).keyConditionExpression("carrierId = :c")
                    .expressionAttributeValues(Map.of(":c", string(carrierId)))
                    .scanIndexForward(true);
            // An empty map is still marshalled as ExclusiveStartKey={}, which DynamoDB rejects.
            if (!lastKey.isEmpty()) {
                query.exclusiveStartKey(lastKey);
            }
            QueryResponse response = dynamo.query(query.build());
            response.items().stream().map(this::toShipment).forEach(items::add);
            lastKey = response.lastEvaluatedKey();
        } while (!lastKey.isEmpty());
        return List.copyOf(items);
    }

    @Override
    public void remove(String shipmentRef) {
        dynamo.deleteItem(DeleteItemRequest.builder().tableName(table)
                .key(Map.of("shipmentRef", string(shipmentRef))).build());
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private ShipmentItem toShipment(Map<String, AttributeValue> attributes) {
        return new ShipmentItem(attributes.get("shipmentRef").s(), attributes.get("manifestNo").s(),
                attributes.get("carrierId").s(), LocalDate.parse(attributes.get("dispatchDate").s()),
                Instant.parse(attributes.get("registeredAt").s()),
                ShipmentState.valueOf(attributes.get("state").s()),
                Integer.parseInt(attributes.get("parcelCount").n()),
                new BigDecimal(attributes.get("totalCharge").n()), attributes.get("manifestKey").s());
    }
}
