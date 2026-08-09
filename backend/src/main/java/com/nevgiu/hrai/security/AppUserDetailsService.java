package com.nevgiu.hrai.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return new AppUserPrincipal(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.getOrganizationId(), user.isEnabled(),
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
    }
}
