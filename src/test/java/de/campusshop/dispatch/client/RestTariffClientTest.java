package de.campusshop.dispatch.client;

import com.sun.net.httpserver.HttpServer;
import de.campusshop.dispatch.config.*;
import de.campusshop.dispatch.mapper.ManifestMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** A real local HTTP stub; no calls to the external reference tariff service. */
class RestTariffClientTest {
    HttpServer server;
    ExecutorService executor;
    RestTariffClient client;
    AtomicInteger requests;
    static final String JSON = """
            {"tariffCode":"TRF-1","serviceName":"Standard","baseFee":4.50,
             "pricePerKg":1.20,"fuelSurchargeRate":0.10,"transitDays":2,"futureField":true}
            """;
    @BeforeEach void startServer() throws IOException {
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        var properties = new TariffServiceProperties("rest", "http://127.0.0.1:" + server.getAddress().getPort(), 150);
        client = new RestTariffClient(new TariffClientConfig().tariffRestClient(RestClient.builder(), properties), new ManifestMapper());
    }
    @AfterEach void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }
    void response(int status, String body, long delay) {
        server.createContext("/tariffs/TRF-1", exchange -> {
            requests.incrementAndGet();
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Expected when the test client times out and closes the connection.
            } finally { exchange.close(); }
        });
    }
    @Test void mapsA200ResponseAndUsesTheConfiguredUrl() {
        response(200, JSON, 0);
        assertThat(client.findTariff("TRF-1")).contains(STANDARD);
        assertThat(requests.get()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(ints = {404, 500, 503})
    void errorsReturnNoTariff(int status) {
        response(status, "{}", 0);
        assertThat(client.findTariff("TRF-1")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"not-json", "{}", "null", "{\"tariffCode\":\"WRONG\",\"serviceName\":\"X\",\"baseFee\":1,\"pricePerKg\":2,\"fuelSurchargeRate\":0}"})
    void invalidOrMismatchedSuccessResponsesAreUnrated(String body) {
        response(200, body, 0);
        assertThat(client.findTariff("TRF-1")).isEmpty();
    }
    @Test void enforcesTheConfiguredReadTimeout() {
        response(200, JSON, 1200);
        long start = System.nanoTime();
        assertThat(client.findTariff("TRF-1")).isEmpty();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(1000);
    }
    @Test void connectionRefusalReturnsNoTariff() {
        server.stop(0);
        assertThat(client.findTariff("TRF-1")).isEmpty();
    }
}
