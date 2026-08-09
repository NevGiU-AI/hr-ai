package com.nevgiu.hrai.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record AppUserPrincipal(
        Long id,
        String username,
        String password,
        String organizationId,
        boolean enabled,
        Collection<SimpleGrantedAuthority> authorities
) implements UserDetails {
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<SimpleGrantedAuthority> getAuthorities() { return authorities; }
}
