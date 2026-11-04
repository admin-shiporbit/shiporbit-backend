package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.core.ChargeLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * ShipOrbit margin on the pre-tax subtotal, then GST on (subtotal + margin).
 *   MARGIN = subtotal x margin%         (2 dp, HALF_UP)
 *   GST    = (subtotal + MARGIN) x gst% (2 dp, HALF_UP)
 * Percentages from config: shiporbit.rates.margin-percent (default 10), shiporbit.rates.gst-percent (default 18).
 */
@Component
public class MarginTaxPolicy {

    public static final String MARGIN = "MARGIN";
    public static final String GST = "GST";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal marginPercent;
    private final BigDecimal gstPercent;

    public MarginTaxPolicy(@Value("${shiporbit.rates.margin-percent:10}") BigDecimal marginPercent,
                           @Value("${shiporbit.rates.gst-percent:18}") BigDecimal gstPercent) {
        if (marginPercent.signum() < 0 || gstPercent.signum() < 0) {
            throw new IllegalArgumentException("margin and GST percent must be >= 0");
        }
        this.marginPercent = marginPercent;
        this.gstPercent = gstPercent;
    }

    public Totals apply(List<ChargeLine> preTaxLines) {
        BigDecimal subtotal = preTaxLines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal margin = percentOf(subtotal, marginPercent);
        BigDecimal taxable = subtotal.add(margin);
        BigDecimal gst = percentOf(taxable, gstPercent);

        List<ChargeLine> lines = new ArrayList<>(preTaxLines);
        lines.add(new ChargeLine(MARGIN, margin));
        lines.add(new ChargeLine(GST, gst));
        return new Totals(List.copyOf(lines), taxable, gst, taxable.add(gst));
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public record Totals(List<ChargeLine> lines, BigDecimal taxableAmount, BigDecimal gst, BigDecimal total) { }
}
