package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.service.RateAggregatorService;
import com.shiporbit.backend.rate.service.ShreeMurtiPartnerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rate")
public class RateAggregatorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateAggregatorController.class);

    // Typed as the concrete ShreeMurtiPartnerClient (not the DeliveryPartnerClient
    // interface) on purpose: /validate exists to hit ShreeMurti specifically, and the
    // interface type is ambiguous now that multiple @Component partner clients exist.
    // Everything that should dispatch across all partners goes through
    // RateAggregatorService (which correctly takes List<DeliveryPartnerClient>).
    private final ShreeMurtiPartnerClient shreeMurtiPartnerClient;
    private final RateAggregatorService rateAggregatorService;

    @Autowired
    public RateAggregatorController(ShreeMurtiPartnerClient shreeMurtiPartnerClient, RateAggregatorService rateAggregatorService) {
        this.shreeMurtiPartnerClient = shreeMurtiPartnerClient;
        this.rateAggregatorService = rateAggregatorService;
    }

    @PostMapping("/aggregator")
    public ResponseEntity<Map<String, RateResponse>> generateResponse(@RequestBody RequestParamRecord requestParameter){
        LOGGER.info("Initiated the Rate calculation for the request for : {}", requestParameter);
        rateAggregatorService.validateRequest(requestParameter);
        Map<String, RateResponse> rateResponses = rateAggregatorService.compareRates(requestParameter);
        LOGGER.info("Rate calculation completed, {} partner(s) returned a quote: {}", rateResponses.size(), rateResponses.keySet());
        return ResponseEntity.ok().body(rateResponses);
    }

    @PostMapping("/validate")
    public void validateAggregatorRequest(){
        LOGGER.info("Initiated ShreeMurti validate check");
        shreeMurtiPartnerClient.getRate(null);
    }
}
