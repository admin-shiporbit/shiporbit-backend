package com.shiporbit.backend.rate.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request of POST /api/v1/rates/compare, in the frontend's format (sample_json/rate_calculator/requet.json).
 * Numbers arrive as strings; the format is checked here and CompareRatesMapper parses and converts units.
 * Any number of decimals is accepted (weight rounded to 3 dp, dimensions to 2 dp after unit conversion).
 * length / width / height are optional: blank or 0 (any of them) = no dimensions -> no volumetric weight.
 */
public record CompareRatesRequest(
        @Pattern(regexp = "(?i)surface|air|express|all", message = "must be Surface, Air, Express or All")
        String deliveryMode,                                    // null = All
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String fromPin,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String toPin,
        @NotBlank @Pattern(regexp = "\\d{1,7}(\\.\\d+)?", message = "must be a number") String weight,
        @Pattern(regexp = "(?i)kg|g", message = "must be kg or g") String weightUnit,           // null = kg
        @Pattern(regexp = "(\\d{1,5}(\\.\\d+)?)?", message = "must be a number") String length,          // optional
        @Pattern(regexp = "(\\d{1,5}(\\.\\d+)?)?", message = "must be a number") String width,           // optional
        @Pattern(regexp = "(\\d{1,5}(\\.\\d+)?)?", message = "must be a number") String height,          // optional
        @Pattern(regexp = "(?i)cm|in|m", message = "must be cm, in or m") String dimensionUnit,  // null = cm
        @JsonProperty("isCOD") Boolean cod,                                                      // null = false
        @Pattern(regexp = "(\\d{1,10}(\\.\\d+)?)?", message = "must be a number") String orderValue       // blank = 0
) {
    public CompareRatesRequest {
        weightUnit = weightUnit == null || weightUnit.isBlank() ? "kg" : weightUnit;
        dimensionUnit = dimensionUnit == null || dimensionUnit.isBlank() ? "cm" : dimensionUnit;
        cod = Boolean.TRUE.equals(cod);
    }
}
