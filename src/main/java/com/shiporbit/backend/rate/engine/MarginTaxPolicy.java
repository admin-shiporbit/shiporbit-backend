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
// Spring bean, shared by rate cards and live APIs.
@Component
// ShipOrbit margin + GST.
public class MarginTaxPolicy {

    // Code of the margin line.
    public static final String MARGIN = "MARGIN";
    // Code of the GST line.
    public static final String GST = "GST";
    // 100.
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // Margin % (shiporbit.rates.margin-percent, default 10).
    private final BigDecimal marginPercent;
    // GST % (shiporbit.rates.gst-percent, default 18).
    private final BigDecimal gstPercent;

    // Values from application.yaml,
    public MarginTaxPolicy(@Value("${shiporbit.rates.margin-percent:10}") BigDecimal marginPercent,
                           // injected by Spring.
                           @Value("${shiporbit.rates.gst-percent:18}") BigDecimal gstPercent) {
        // Negative values?
        if (marginPercent.signum() < 0 || gstPercent.signum() < 0) {
            // -> fail at startup.
            throw new IllegalArgumentException("margin and GST percent must be >= 0");
        }
        // Store margin %.
        this.marginPercent = marginPercent;
        // Store GST %.
        this.gstPercent = gstPercent;
    }

    // Add margin and GST to the pre-tax lines.
    public Totals apply(List<ChargeLine> preTaxLines) {
        // Pre-tax subtotal = sum of all lines.
        BigDecimal subtotal = preTaxLines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Margin = subtotal x margin% (2 dp).
        BigDecimal margin = percentOf(subtotal, marginPercent);
        // Taxable = subtotal + margin.
        BigDecimal taxable = subtotal.add(margin);
        // GST = taxable x GST% (2 dp).
        BigDecimal gst = percentOf(taxable, gstPercent);

        // Copy lines,
        List<ChargeLine> lines = new ArrayList<>(preTaxLines);
        // add MARGIN,
        lines.add(new ChargeLine(MARGIN, margin));
        // add GST.
        lines.add(new ChargeLine(GST, gst));
        // Totals: lines, taxable, GST, total = taxable + GST.
        return new Totals(List.copyOf(lines), taxable, gst, taxable.add(gst));
    }

    // amount x percent / 100,
    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        // 2 dp HALF_UP.
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    // Result record.
    public record Totals(List<ChargeLine> lines, BigDecimal taxableAmount, BigDecimal gst, BigDecimal total) { }
}
