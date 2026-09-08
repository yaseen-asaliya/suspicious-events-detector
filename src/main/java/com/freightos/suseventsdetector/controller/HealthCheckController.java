package com.freightos.suseventsdetector.controller;

import com.freightos.suseventsdetector.service.HealthCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/health")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    public HealthCheckController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping(path = "/is-alive")
    public ResponseEntity<Void> isAlive() {
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/is-ready")
    public ResponseEntity<Void> isReady() {
        boolean isReady = healthCheckService.isReady();
        if(isReady) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
