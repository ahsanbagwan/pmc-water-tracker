package org.punewatertracker.service;

import org.punewatertracker.model.Locality;
import org.punewatertracker.model.WaterStatus;
import org.punewatertracker.repository.LocalityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class LocalityService {

    private final LocalityRepository repository;

    public LocalityService(LocalityRepository repository) {
        this.repository = repository;
    }

    /** Publicly visible localities: verified entries only, optionally narrowed by status or name. */
    public List<Locality> findVisible(WaterStatus status, String search) {
        if (status != null) {
            return repository.findByStatus(status).stream()
                    .filter(Locality::isVerified)
                    .toList();
        }
        if (search != null && !search.isBlank()) {
            return repository.findByNameContainingIgnoreCaseAndVerifiedTrue(search);
        }
        return repository.findByVerifiedTrue();
    }

    public Locality findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No locality with id " + id));
    }

    /** Admin-curated entry, backed by a checkable source — goes live immediately. */
    public Locality createVerified(Locality locality) {
        locality.setId(null);
        locality.setVerified(true);
        if (locality.getLastVerified() == null) {
            locality.setLastVerified(LocalDate.now());
        }
        return repository.save(locality);
    }

    /** Citizen-submitted correction/report — held back from the public view until an admin confirms it. */
    public Locality createUnverifiedReport(Locality locality) {
        locality.setId(null);
        locality.setVerified(false);
        locality.setLastVerified(LocalDate.now());
        return repository.save(locality);
    }

    public List<Locality> findPendingReports() {
        return repository.findAll().stream()
                .filter(l -> !l.isVerified())
                .toList();
    }

    public Locality approveReport(Long id) {
        Locality locality = findById(id);
        locality.setVerified(true);
        return repository.save(locality);
    }

    public Locality update(Long id, Locality patch) {
        Locality existing = findById(id);
        existing.setName(patch.getName());
        existing.setWard(patch.getWard());
        existing.setStatus(patch.getStatus());
        existing.setLatitude(patch.getLatitude());
        existing.setLongitude(patch.getLongitude());
        existing.setNotes(patch.getNotes());
        existing.setSourceName(patch.getSourceName());
        existing.setSourceUrl(patch.getSourceUrl());
        existing.setLastVerified(patch.getLastVerified() != null ? patch.getLastVerified() : LocalDate.now());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
