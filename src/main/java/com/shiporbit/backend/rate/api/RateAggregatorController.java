package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.service.RateAggregatorService;
import com.shiporbit.backend.rate.service.RateAggregatorServiceImpl;
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

    private final RateAggregatorService rateAggregatorService;

    @Autowired
    public RateAggregatorController(RateAggregatorServiceImpl rateAggregatorService) {
        this.rateAggregatorService = rateAggregatorService;
    }

    @PostMapping("/aggregator")
    public ResponseEntity<Map<String, RateResponse>> generateResponse(@RequestBody RequestParamRecord requestParameter){
        LOGGER.info("Initiated the Rate calculation for the request for : {}", requestParameter);
        rateAggregatorService.validateRequest(requestParameter);
        return ResponseEntity.ok().body(rateAggregatorService.compareRates(requestParameter));
    }

}
