package com.shiporbit.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record RateQuoteRequest(

        @NotBlank
        String sourceState,

        @NotBlank
        String destState,

        String pickupPincode,

        String deliveryPincode,

        @NotNull
        @PositiveOrZero
        Double actualWeightKg,

        @Valid
        List<Dimension> packages,

        @NotNull
        @PositiveOrZero
        Double declaredValue,

        /** "OWNER" or "CARRIER" - defaults to CARRIER when omitted. */
        String riskType,

        @Min(1)
        Integer numberOfLRs,

        // Boxed (not primitive) so the field can be omitted from the JSON body -
        // Jackson can't map a missing property into a primitive boolean.
        Boolean floorDelivery,
        Boolean mallDelivery,
        Boolean csdArmyDelivery,
        Boolean sundayOrHolidayDelivery,
        Boolean toPay,
        Boolean chequePayment,

        @PositiveOrZero
        Double collectableCodAmount
) {
}
