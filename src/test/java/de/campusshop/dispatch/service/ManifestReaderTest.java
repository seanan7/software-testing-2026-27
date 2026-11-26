package de.campusshop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.dispatch.exception.ManifestValidationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ManifestReaderTest {
    @Test void violationsNameTheInvalidFieldAndExplainTheConstraint() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var mapper = new ObjectMapper().findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            var reader = new ManifestReader(mapper, factory.getValidator());
            byte[] invalid = new String(raw(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\"weightKg\":2.5", "\"weightKg\":0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThatThrownBy(() -> reader.read(invalid)).isInstanceOfSatisfying(ManifestValidationException.class,
                    ex -> assertThat(ex.violations()).anyMatch(v -> v.startsWith("parcels[0].weightKg:") && v.contains("greater than 0")));
        }
    }
}
