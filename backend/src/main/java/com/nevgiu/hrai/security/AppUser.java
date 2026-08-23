package com.nevgiu.hrai.security;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email"))
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String organizationId;

    @Column(nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<AppRole> roles = new LinkedHashSet<>();

    protected AppUser() {}

    public AppUser(String email, String passwordHash, String organizationId, Set<AppRole> roles) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.organizationId = organizationId;
        this.roles = new LinkedHashSet<>(roles);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getOrganizationId() { return organizationId; }
    public boolean isEnabled() { return enabled; }
    public Set<AppRole> getRoles() { return Set.copyOf(roles); }

    public void replaceRoles(Set<AppRole> roles) {
        this.roles = new LinkedHashSet<>(roles);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
