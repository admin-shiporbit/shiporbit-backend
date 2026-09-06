package com.shiporbit.backend.rate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carrier-agnostic rate breakdown. Every {@link com.shiporbit.backend.rate.DeliveryRateStrategy}
 * implementation returns one of these, regardless of which delivery partner produced it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateBreakdown {
    public double actualWeightKg;
    public double volumetricWeightKg;
    public double chargeableWeightKg;
    public double ratePerKg;
    public double baseFreight;
    public double fsc;
    public double processing;
    public double rov;
    public double handling;
    public double oda;
    public double floorDeliveryCharge;
    public double mallDeliveryCharge;
    public double csdArmyCharge;
    public double sundayHolidayCharge;
    public double toPayCharge;
    public double chequeHandlingCharge;
    public double cashHandlingCharge;
    public double greenTax;
    public double subtotal;
    public double total;

    // GST (forward charge) - applied on `total` by RateCalculator, uniformly across every
    // partner. gstRate is a percentage (e.g. 18.0). Exactly one of {cgst+sgst} or {igst} is
    // non-zero: CGST+SGST for an intra-state shipment, IGST for inter-state.
    public double gstRate;
    public double cgst;
    public double sgst;
    public double igst;
    public double gstAmount;
    public double grandTotal;
}
