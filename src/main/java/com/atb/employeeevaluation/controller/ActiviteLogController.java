package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.service.ActiviteLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activites")
@RequiredArgsConstructor
public class ActiviteLogController {

    private final ActiviteLogService activiteLogService;

    @GetMapping
    public ResponseEntity<List<ActiviteLogDTO>> getRecentActivities(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(activiteLogService.getRecentActivities(limit));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        activiteLogService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
