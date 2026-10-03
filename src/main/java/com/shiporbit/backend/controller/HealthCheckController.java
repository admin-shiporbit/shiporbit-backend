package com.shiporbit.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class HealthCheckController {

    @GetMapping("/health")
    public String healthCheck(){
        return "Health - OK";
    }
}
