package com.ticketflow.domain.performance.controller;

import com.ticketflow.domain.performance.dto.PerformanceResponse;
import com.ticketflow.domain.performance.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/api/performances")
    public ResponseEntity<List<PerformanceResponse>> getPerformances() {
        List<PerformanceResponse> performanceResponseList = performanceService.getPerformances();

        return ResponseEntity.ok(performanceResponseList);
    }
}
