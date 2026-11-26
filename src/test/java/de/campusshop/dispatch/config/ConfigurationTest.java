package de.campusshop.dispatch.config;

import org.junit.jupiter.api.Test;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {
    @Test void buildsExternalSdkClientsFromProvidedSettingsWithoutProvisioningResources() {
        var properties = new DispatchProperties(new DispatchProperties.Archive("test-bucket"),
                new DispatchProperties.Shipments("test-table"),
                new DispatchProperties.Aws("http://127.0.0.1:1", "us-east-1", "test", "test"));
        var factory = new AwsClientsConfig(properties);
        try (var s3 = factory.s3Client(); var dynamo = factory.dynamoDbClient()) {
            assertThat(s3.serviceName()).isEqualTo("s3");
            assertThat(dynamo.serviceName()).isEqualTo("dynamodb");
        }
    }
    @Test void registrationClockUsesUtc() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
