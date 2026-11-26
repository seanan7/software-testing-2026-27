package de.campusshop.dispatch.controller;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import de.campusshop.dispatch.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full controller -> services -> repositories flow, without external network dependencies. */
@SpringBootTest(properties = "tariff-service.mode=simulation")
@ActiveProfiles("inmemory")
@AutoConfigureMockMvc
class ShipmentApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean ShipmentRepository shipments;
    @MockitoSpyBean ManifestArchive archive;
    String manifestNo;
    String carrierId;
    byte[] body;
    @BeforeEach void prepare() {
        manifestNo = UUID.randomUUID().toString();
        carrierId = UUID.randomUUID().toString();
        body = raw(manifestNo, carrierId);
        clearInvocations(shipments, archive);
    }
    @Test void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    JsonNode register(byte[] bytes) throws Exception {
        var response = mvc.perform(post("/dispatch/manifests").contentType("application/json").content(bytes))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
    @Test void registerReadListAndDeleteIncludingBothStorageSideEffects() throws Exception {
        var record = register(body);
        String ref = record.path("shipmentRef").asText();
        String key = "manifests/" + carrierId + "/" + ref + ".json";
        assertThat(UUID.fromString(ref).toString()).isEqualTo(ref);
        assertThat(record.path("manifestNo").asText()).isEqualTo(manifestNo);
        assertThat(record.path("carrierId").asText()).isEqualTo(carrierId);
        assertThat(record.path("state").asText()).isEqualTo("REGISTERED");
        assertThat(record.path("parcelCount").asInt()).isEqualTo(2);
        assertThat(record.path("totalCharge").decimalValue()).isEqualByComparingTo("16.99");
        assertThat(record.path("parcels").get(0).path("charge").decimalValue()).isEqualByComparingTo("8.25");
        assertThat(record.path("parcels").get(1).path("rating").asText()).isEqualTo("RATED");
        assertThat(shipments.get(ref)).hasValueSatisfying(i -> {
            assertThat(i.manifestKey()).isEqualTo(key);
            assertThat(i.totalCharge()).isEqualByComparingTo("16.99");
            assertThat(i.parcelCount()).isEqualTo(2);
        });
        assertThat(archive.fetch(key)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(body));
        mvc.perform(get("/shipments/" + ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestKey").value(key)).andExpect(jsonPath("$.parcels").doesNotExist());
        mvc.perform(get("/shipments/" + ref + "/manifest")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(content().bytes(body));
        mvc.perform(get("/carriers/" + carrierId + "/shipments")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shipmentRef").value(ref));
        mvc.perform(delete("/shipments/" + ref)).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(shipments.get(ref)).isEmpty();
        assertThat(archive.fetch(key)).isEmpty();
        mvc.perform(get("/shipments/" + ref)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("SHIPMENT_NOT_FOUND"));
        mvc.perform(get("/shipments/" + ref + "/manifest")).andExpect(status().isNotFound());
        mvc.perform(delete("/shipments/" + ref)).andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"manifest-missing", "manifest-blank", "manifest-format", "carrier-missing", "carrier-id-missing",
            "carrier-id-format", "email", "date-missing", "date-format", "date-impossible", "parcels-missing", "parcels-empty",
            "parcel-null", "code-blank", "weight-missing", "weight-zero", "weight-negative", "weight-text"})
    void invalidManifestsWriteNothing(String kind) throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(body);
        ObjectNode carrier = (ObjectNode) root.path("carrier");
        ArrayNode parcels = (ArrayNode) root.path("parcels");
        ObjectNode first = (ObjectNode) parcels.get(0);
        switch (kind) {
            case "manifest-missing" -> root.remove("manifestNo");
            case "manifest-blank" -> root.put("manifestNo", " ");
            case "manifest-format" -> root.put("manifestNo", "not-a-uuid");
            case "carrier-missing" -> root.remove("carrier");
            case "carrier-id-missing" -> carrier.remove("carrierId");
            case "carrier-id-format" -> carrier.put("carrierId", "invalid");
            case "email" -> carrier.put("contactEmail", "not-an-email");
            case "date-missing" -> root.remove("dispatchDate");
            case "date-format" -> root.put("dispatchDate", "20-11-2026");
            case "date-impossible" -> root.put("dispatchDate", "2026-02-30");
            case "parcels-missing" -> root.remove("parcels");
            case "parcels-empty" -> parcels.removeAll();
            case "parcel-null" -> parcels.set(0, NullNode.instance);
            case "code-blank" -> first.put("tariffCode", " ");
            case "weight-missing" -> first.remove("weightKg");
            case "weight-zero" -> first.put("weightKg", 0);
            case "weight-negative" -> first.put("weightKg", -1);
            case "weight-text" -> first.put("weightKg", "heavy");
            default -> throw new AssertionError(kind);
        }
        mvc.perform(post("/dispatch/manifests").contentType("application/json").content(json.writeValueAsBytes(root)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isNotEmpty()).andExpect(jsonPath("$.occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/dispatch/manifests"));
        verify(shipments, never()).put(any());
        verify(archive, never()).archive(anyString(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"", "{", "{\"parcels\":[", "not-json"})
    void malformedBodiesAre400AndWriteNothing(String invalid) throws Exception {
        mvc.perform(post("/dispatch/manifests").contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("MALFORMED_MANIFEST"));
        verifyNoInteractions(shipments, archive);
    }
    @Test void nullAndTrailingDocumentsAreRejected() throws Exception {
        for (String invalid : List.of("null", new String(body, java.nio.charset.StandardCharsets.UTF_8) + " {}")) {
            mvc.perform(post("/dispatch/manifests").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(shipments, archive);
    }
    @Test void duplicateManifestIs409AndCreatesNoNewObjects() throws Exception {
        var original = register(body);
        clearInvocations(shipments, archive);
        mvc.perform(post("/dispatch/manifests").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("DUPLICATE_MANIFEST"));
        verify(archive, never()).archive(anyString(), any());
        verify(shipments, never()).put(any());
        assertThat(shipments.listByCarrier(carrierId)).hasSize(1);
        assertThat(archive.fetch(original.path("manifestKey").asText())).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(body));
    }
    @Test void unknownTariffIsKeptWithEveryNullFieldPresent() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(body);
        ((ObjectNode) root.path("parcels").get(1)).put("tariffCode", "TRF-9999");
        var record = register(json.writeValueAsBytes(root));
        assertThat(record.path("parcelCount").asInt()).isEqualTo(2);
        assertThat(record.path("totalCharge").decimalValue()).isEqualByComparingTo("8.25");
        var parcel = record.path("parcels").get(1);
        assertThat(parcel.path("rating").asText()).isEqualTo("UNRATED");
        assertThat(parcel.path("parcelNo").asInt()).isEqualTo(2);
        for (String field : List.of("serviceName", "baseFee", "pricePerKg", "fuelSurchargeRate", "charge")) {
            assertThat(parcel.has(field)).isTrue();
            assertThat(parcel.path(field).isNull()).isTrue();
        }
    }
    @Test void listingIsOldestFirstAndDoesNotMixCarriers() throws Exception {
        for (String date : List.of("2026-12-20", "2026-01-01", "2026-06-01")) {
            ObjectNode root = (ObjectNode) json.readTree(raw(UUID.randomUUID().toString(), carrierId));
            root.put("dispatchDate", date);
            register(json.writeValueAsBytes(root));
        }
        register(raw(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        var response = mvc.perform(get("/carriers/" + carrierId + "/shipments")).andExpect(status().isOk()).andReturn();
        var rows = json.readTree(response.getResponse().getContentAsByteArray());
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).path("dispatchDate").asText()).isEqualTo("2026-01-01");
        assertThat(rows.get(1).path("dispatchDate").asText()).isEqualTo("2026-06-01");
        assertThat(rows.get(2).path("dispatchDate").asText()).isEqualTo("2026-12-20");
    }
    @Test void unknownReferencesAre404AndEmptyCarriersReturnAnArray() throws Exception {
        for (String suffix : List.of("", "/manifest")) {
            mvc.perform(get("/shipments/00000000-0000-4000-8000-000000000000" + suffix))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("SHIPMENT_NOT_FOUND"));
        }
        mvc.perform(delete("/shipments/00000000-0000-4000-8000-000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/carriers/" + carrierId + "/shipments")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void invalidPathIdentifiersAre400() throws Exception {
        mvc.perform(get("/shipments/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/carriers/invalid/shipments")).andExpect(status().isBadRequest());
        mvc.perform(delete("/shipments/invalid")).andExpect(status().isBadRequest());
    }
    @Test void unexpectedStorageFailureReturns500AndCompensatesTheArchive() throws Exception {
        doThrow(new IllegalStateException("simulated index failure")).when(shipments).put(any());
        try {
            mvc.perform(post("/dispatch/manifests").contentType("application/json").content(body))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
            var key = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(archive).archive(key.capture(), any());
            verify(archive).remove(key.getValue());
            assertThat(archive.fetch(key.getValue())).isEmpty();
            assertThat(shipments.listByCarrier(carrierId)).isEmpty();
        } finally { doCallRealMethod().when(shipments).put(any()); }
    }
}
