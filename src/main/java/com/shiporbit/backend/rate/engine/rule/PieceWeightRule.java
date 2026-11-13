package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.core.ShipmentPackage;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * No piece may weigh more than the card's MAX_PIECE_WEIGHT_KG (dead weight per box). Used where heavier
 * pieces are charged "on actuals" (crane / MHE) and so cannot be quoted. Null limit = rule off.
 */
// Limit rule 2: every piece within MAX_PIECE_WEIGHT_KG.
public final class PieceWeightRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Card's per-piece maximum.
        BigDecimal max = ctx.card().maxPieceWeightKg();
        // Not set (most cards)?
        if (max == null) {
            // Rule off.
            return Optional.empty();
        }
        // Over all package lines:
        return ctx.request().packages().stream()
                // dead weight of one piece,
                .map(ShipmentPackage::deadWeightKg)
                // heavier than the maximum,
                .filter(w -> w.compareTo(max) > 0)
                // first such piece,
                .findFirst()
                // -> WEIGHT_LIMIT_EXCEEDED; none -> empty (passed).
                .map(w -> new Rejection(QuoteStatus.WEIGHT_LIMIT_EXCEEDED,
                        "Piece weight " + w.stripTrailingZeros().toPlainString() + " kg exceeds max "
                                + max.stripTrailingZeros().toPlainString() + " kg per piece"));
    }
}
