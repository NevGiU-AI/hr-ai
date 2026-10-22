package com.nevgiu.hrai.candidate;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "candidates")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identifies the organization that owns this candidate. Repository queries and service operations
     * must include this value so candidate data cannot cross tenant boundaries.
     */
    @Column(nullable = false, length = 100, columnDefinition = "varchar(100) default 'default'")
    private String organizationId;

    private String name;

    /**
     * The value {@code 320} follows the commonly used maximum mailbox length: up to 64 characters
     * for the local part, {@code @}, and up to 255 characters for the domain. This mapping must remain
     * aligned with request validation and the database column to avoid accepting an address that
     * persistence would later reject.
     */
    @Column(length = 320)
    private String email;
    private String location;
    private String currentTitle;
    private Integer yearsExperience;

    /**
     * Extracted CV text used by the recruitment workflow. It contains personal data and must only be
     * exposed through tenant-scoped, authorized operations; the original uploaded file is stored
     * separately by the original-CV storage subsystem.
     */
    @Lob
    private String cvText;

    /** Records when the candidate row was first created; metadata corrections do not replace it. */
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCurrentTitle() { return currentTitle; }
    public void setCurrentTitle(String currentTitle) { this.currentTitle = currentTitle; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getCvText() { return cvText; }
    public void setCvText(String cvText) { this.cvText = cvText; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
