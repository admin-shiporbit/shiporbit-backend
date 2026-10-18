package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.service.DelhiveryB2cRateClient;
import com.shiporbit.backend.rate.service.DelhiveryPartnerClient;
import com.shiporbit.backend.rate.service.ShreeMurtiPartnerClient;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCheckRequest;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCheckResponse;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCheckResponse.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Partner serviceability check, same calls the quote flow makes (and the same cache).
 * POST /api/v1/rates/serviceability - for testing partner APIs and for the frontend's pincode check.
 * To bypass cached answers: POST /api/v1/rate-cards/cache/evict (ADMIN) first.
 */
@RestController
@RequestMapping("/api/v1/rates/serviceability")
public class ServiceabilityController {

    private final DelhiveryPartnerClient delhivery;
    private final DelhiveryB2cRateClient delhiveryB2c;
    private final ShreeMurtiPartnerClient shreeMurti;

    public ServiceabilityController(DelhiveryPartnerClient delhivery, DelhiveryB2cRateClient delhiveryB2c,
                                    ShreeMurtiPartnerClient shreeMurti) {
        this.delhivery = delhivery;
        this.delhiveryB2c = delhiveryB2c;
        this.shreeMurti = shreeMurti;
    }

    @PostMapping
    public ServiceabilityCheckResponse check(@Valid @RequestBody ServiceabilityCheckRequest request) {
        RequestParamRecord params = toParams(request);
        List<Result> results = new ArrayList<>();
        if (request.wants("DELHIVERY")) {
            String api = delhiveryB2c.handles(params) ? "B2C" : "LTL";
            results.add(result("DELHIVERY", api, delhivery.checkServiceability(params)));
        }
        if (request.wants("SHREE_MARUTI")) {
            results.add(result("SHREE_MARUTI", "ECOMM", shreeMurti.checkServiceability(params)));
            if (request.hyperlocalPickup() != null || request.hyperlocalDrop() != null) {
                results.add(result("SHREE_MARUTI", "HYPERLOCAL", shreeMurti.checkHyperlocal(
                        request.hyperlocalPickup(), request.hyperlocalDrop(), request.paymentMode() == PaymentMode.COD)));
            }
        }
        return new ServiceabilityCheckResponse(request.originPincode(), request.destinationPincode(), results);
    }

    private static Result result(String partner, String api, Serviceability s) {
        return new Result(partner, api, s.status(), s.errorCode() == null ? null : s.errorCode().code(), s.reason());
    }

    /** Same shape LivePartnerQuoteProvider builds: weight in grams, Delhivery payment_mode cod/prepaid, ShreeMurti cod flag. */
    private static RequestParamRecord toParams(ServiceabilityCheckRequest r) {
        boolean cod = r.paymentMode() == PaymentMode.COD;
        Dimension d = new Dimension();
        d.setLength(dbl(r.lengthCm()));
        d.setWidth(dbl(r.widthCm()));
        d.setHeight(dbl(r.heightCm()));
        d.setBoxCount(1);
        return new RequestParamRecord(
                r.weightKg().multiply(BigDecimal.valueOf(1000)).doubleValue(),
                r.originPincode(), r.destinationPincode(), d, false,
                cod ? "cod" : "prepaid", 0.0, null, false,
                Map.of("cod", cod));
    }

    private static Double dbl(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
