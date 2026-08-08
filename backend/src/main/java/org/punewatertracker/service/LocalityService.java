package org.punewatertracker.service;

import org.punewatertracker.messaging.ReportEventPublisher;
import org.punewatertracker.messaging.ReportSubmittedEvent;
import org.punewatertracker.model.Locality;
import org.punewatertracker.model.WaterStatus;
import org.punewatertracker.repository.LocalityRepository;
import org.springframework.stereotype.Service;

import org.punewatertracker.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class LocalityService {

    private final LocalityRepository repository;
    private final ReportEventPublisher reportEventPublisher;

    public LocalityService(LocalityRepository repository,
                           ReportEventPublisher reportEventPublisher) {
        this.repository = repository;
        this.reportEventPublisher = reportEventPublisher;
    }

    /** Publicly visible localities: verified entries only, optionally narrowed by status or name. */
    @Cacheable(value = CacheConfig.LOCALITIES_CACHE, key = "'list:status=' + #status + ',search=' + #search")
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

    @Cacheable(value = CacheConfig.LOCALITIES_CACHE, key = "'id:' + #id")
    public Locality findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No locality with id " + id));
    }

    /** Admin-curated entry, backed by a checkable source — goes live immediately. */
    @CacheEvict(value = CacheConfig.LOCALITIES_CACHE, allEntries = true)
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
        Locality saved = repository.save(locality);

        reportEventPublisher.publishReportSubmitted(new ReportSubmittedEvent(
                saved.getId(), saved.getName(), saved.getStatus().name(), saved.getNotes(), Instant.now()));

        return saved;
    }

    public List<Locality> findPendingReports() {
        return repository.findAll().stream()
                .filter(l -> !l.isVerified())
                .toList();
    }

    @CacheEvict(value = CacheConfig.LOCALITIES_CACHE, allEntries = true)
    public Locality approveReport(Long id) {
        Locality locality = findById(id);
        locality.setVerified(true);
        return repository.save(locality);
    }

    @CacheEvict(value = CacheConfig.LOCALITIES_CACHE, allEntries = true)
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

    @CacheEvict(value = CacheConfig.LOCALITIES_CACHE, allEntries = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
