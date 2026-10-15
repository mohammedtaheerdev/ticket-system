package com.ticket.system.service;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.AppUser;
import com.ticket.system.model.Organization;
import com.ticket.system.model.Role;
import com.ticket.system.repository.OrganizationRepository;
import com.ticket.system.repository.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(OrganizationRepository organizations, UserRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApiDtos.LoginView register(ApiDtos.OrganizationRegistration request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (organizations.existsByNameIgnoreCase(request.organizationName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization name is already registered");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        Organization organization = organizations.save(new Organization(request.organizationName().trim()));
        AppUser admin = users.save(new AppUser(request.adminName().trim(), email,
                passwordEncoder.encode(request.password()), Role.ORGANIZATION_ADMIN, organization));
        return DtoMapper.login(admin);
    }
}
