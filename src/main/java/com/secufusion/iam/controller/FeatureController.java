package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateFeatureRequest;
import com.secufusion.iam.dto.FeatureResponse;
import com.secufusion.iam.service.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/features")
public class FeatureController {

    @Autowired
    private FeatureService featureService;

    @PostMapping
    public ResponseEntity<FeatureResponse> createFeature(@RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.createFeature(request));
    }

    @GetMapping
    public ResponseEntity<List<FeatureResponse>> getAllFeatures() {
        return ResponseEntity.ok(featureService.getAllFeatures());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeatureResponse> getFeature(@PathVariable Long id) {
        return ResponseEntity.ok(featureService.getFeature(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FeatureResponse> updateFeature(
            @PathVariable Long id,
            @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.updateFeature(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFeature(@PathVariable Long id) {
        featureService.deleteFeature(id);
        return ResponseEntity.ok("Feature deleted successfully.");
    }
}
