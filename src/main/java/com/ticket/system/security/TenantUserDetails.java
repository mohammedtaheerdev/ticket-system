package com.ticket.system.security;

import com.ticket.system.model.AppUser;
import com.ticket.system.model.Organization;
import com.ticket.system.model.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class TenantUserDetails implements UserDetails {
    private final AppUser user;

    public TenantUserDetails(AppUser user) {
        this.user = user;
    }

    public Long getId() { return user.getId(); }
    public Long getOrganizationId() { return user.getOrganization().getId(); }
    public Organization getOrganization() { return user.getOrganization(); }
    public AppUser getUser() { return user; }
    public String getName() { return user.getName(); }
    public Role getRole() { return user.getRole(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public String getUsername() { return user.getEmail(); }
}
