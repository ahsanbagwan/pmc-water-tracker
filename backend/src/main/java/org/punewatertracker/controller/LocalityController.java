package org.punewatertracker.controller;

import jakarta.validation.Valid;
import org.punewatertracker.model.Locality;
import org.punewatertracker.model.WaterStatus;
import org.punewatertracker.service.LocalityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/localities")
public class LocalityController {

    private final LocalityService service;

    public LocalityController(LocalityService service) {
        this.service = service;
    }

    /** GET /api/localities?status=TANKER_DEPENDENT&search=wagholi */
    @GetMapping
    public List<Locality> list(
            @RequestParam(required = false) WaterStatus status,
            @RequestParam(required = false) String search) {
        return service.findVisible(status, search);
    }

    @GetMapping("/{id}")
    public Locality get(@PathVariable Long id) {
        return service.findById(id);
    }

    /** Admin-curated addition, treated as verified immediately. */
    @PostMapping
    public ResponseEntity<Locality> create(@Valid @RequestBody Locality locality) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createVerified(locality));
    }

    /** Citizen correction/report — queued for admin review, not shown publicly until approved. */
    @PostMapping("/reports")
    public ResponseEntity<Locality> report(@Valid @RequestBody Locality locality) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUnverifiedReport(locality));
    }

    @GetMapping("/reports/pending")
    public List<Locality> pendingReports() {
        return service.findPendingReports();
    }

    @PostMapping("/reports/{id}/approve")
    public Locality approve(@PathVariable Long id) {
        return service.approveReport(id);
    }

    @PutMapping("/{id}")
    public Locality update(@PathVariable Long id, @Valid @RequestBody Locality locality) {
        return service.update(id, locality);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
