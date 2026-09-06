package com.shiporbit.backend.rate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common freight-config fields shared by every delivery partner's rate card
 * (fuel surcharge, risk cover, handling, ODA, conditional delivery charges, ...).
 * Each partner extends this with its own {@code @Component} (e.g. DelhiveryRateCardConfig)
 * and sets its own defaults/overrides in its constructor.
 */
public abstract class RateCardConfig {

    public double volumetricDivisor = 4500;
    public double fscPercent = 20;
    public double processingChargePerLR = 150;

    public double rovOwnerPercent = 0.1;
    public double rovOwnerMinPerLR = 150;
    public double rovCarrierPercent = 0.5;
    public double rovCarrierMinPerLR = 200;

    /** Each entry: {minKgInclusive, maxKgExclusive, ratePerKg} */
    public List<double[]> handlingSlabs = new ArrayList<>();

    /** Each entry: {minKgInclusive, maxKgExclusive, ratePerKg, minCharge} */
    public List<double[]> odaSlabs = new ArrayList<>();
    public int odaMode = 1; // 1=destination pincode, 2=pickup pincode, 3=both
    public Set<String> pickupOdaExemptPincodes = new HashSet<>();
    public Set<String> deliveryOdaExemptPincodes = new HashSet<>();

    /**
     * ODA applies to every pincode NOT in the exempt sets above, so an empty exempt set
     * means ODA fires on every single shipment - not "nothing is ODA," the opposite.
     * Default false until a partner has the real exempt-pincode list to populate those
     * sets with; flip to true (and populate the sets) once that data exists.
     */
    public boolean odaEnabled = false;

    public double minChargeableWeightPerLRKg = 20;
    public double minLrCharge = 350;

    public double floorDeliveryPerKg = 0, floorDeliveryMinPerLR = 0;
    public double mallDeliveryPerKg = 4, mallDeliveryMinPerLR = 750;
    public double csdArmyPerKg = 4, csdArmyMinPerLR = 750;
    public double sundayHolidayPerLR = 0;
    public double toPayPerLR = 100;
    public double chequeHandlingPerLR = 300;
    public double cashHandlingPercent = 2, cashHandlingMinPerLR = 300;
    public double greenTaxPerKg = 0.5, greenTaxMinPerLR = 100;

    public boolean roundOffTotal = true;
}
