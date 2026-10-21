package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Immutable snapshot of candidate metadata after an authorized manual correction. */
@Entity
@Table(name = "candidate_metadata_revisions")
public class CandidateMetadataRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String organizationId;
    @Column(nullable = false) private Long candidateId;
    @Column(nullable = false) private Long actorUserId;
    @Column(nullable = false, length = 320) private String actorEmail;
    private String name;
    @Column(length = 320) private String email;
    private String location;
    private String currentTitle;
    private Integer yearsExperience;
    @Column(nullable = false) private Instant createdAt;

    protected CandidateMetadataRevision() { }

    public CandidateMetadataRevision(Candidate candidate, AppUserPrincipal actor) {
        this.organizationId = candidate.getOrganizationId();
        this.candidateId = candidate.getId();
        this.actorUserId = actor.id();
        this.actorEmail = actor.username();
        this.name = candidate.getName();
        this.email = candidate.getEmail();
        this.location = candidate.getLocation();
        this.currentTitle = candidate.getCurrentTitle();
        this.yearsExperience = candidate.getYearsExperience();
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCandidateId() { return candidateId; }
    public String getActorEmail() { return actorEmail; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }
    public String getCurrentTitle() { return currentTitle; }
    public Integer getYearsExperience() { return yearsExperience; }
    public Instant getCreatedAt() { return createdAt; }
}
