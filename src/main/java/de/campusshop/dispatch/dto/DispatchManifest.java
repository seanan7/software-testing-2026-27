package de.campusshop.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound body of {@code POST /dispatch/manifests}. Fields the service does not know are ignored
 * here; they survive only in the archived raw manifest.
 */
public record DispatchManifest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String manifestNo,
        @NotNull @Valid CarrierDto carrier,
        @NotNull @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE) LocalDate dispatchDate,
        String originWarehouse,
        @NotEmpty List<@NotNull @Valid ParcelDto> parcels) {
}
