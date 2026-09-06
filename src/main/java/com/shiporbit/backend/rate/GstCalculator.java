package com.shiporbit.backend.rate;

import com.shiporbit.backend.constants.Constants;
import com.shiporbit.backend.rate.dto.RateBreakdown;

/**
 * Applies forward-charge GST on top of a partner's pre-tax {@link RateBreakdown#total}.
 * Deliberately partner-agnostic - tax law doesn't vary by delivery partner, so this runs
 * once in {@link RateCalculator} rather than being duplicated in every strategy.
 *
 * NOTE: the intra-/inter-state split here is a same-state-name heuristic, not a full
 * implementation of GST's statutory place-of-supply rules for GTA/courier services
 * (IGST Act s.12(8)). Verify against your actual registration and invoicing setup
 * before relying on this for real invoices.
 */
public final class GstCalculator {

    private GstCalculator() {
    }

    public static void apply(RateBreakdown breakdown, ShipmentRequest request) {
        breakdown.gstRate = Constants.GST_RATE_PERCENT;

        if (isSameState(request.getSourceStateOrCity(), request.getDestStateOrCity())) {
            double half = round2(breakdown.total * (Constants.GST_RATE_PERCENT / 2) / 100.0);
            breakdown.cgst = half;
            breakdown.sgst = half;
            breakdown.igst = 0;
        } else {
            // Also the default when either state is missing/unknown - safer to assume
            // inter-state (IGST) than to wrongly split CGST+SGST without proof they match.
            breakdown.cgst = 0;
            breakdown.sgst = 0;
            breakdown.igst = round2(breakdown.total * Constants.GST_RATE_PERCENT / 100.0);
        }

        breakdown.gstAmount = round2(breakdown.cgst + breakdown.sgst + breakdown.igst);
        breakdown.grandTotal = round2(breakdown.total + breakdown.gstAmount);
    }

    private static boolean isSameState(String sourceState, String destState) {
        return sourceState != null && destState != null
                && sourceState.trim().equalsIgnoreCase(destState.trim());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
