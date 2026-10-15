package com.ticket.system.service;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.AppUser;
import com.ticket.system.model.Role;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.TenantUserDetails;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> list(TenantUserDetails actor) {
        return users.findAllByOrganizationIdOrderByNameAsc(actor.getOrganizationId())
                .stream().map(DtoMapper::user).toList();
    }

    @Transactional
    public ApiDtos.UserView create(TenantUserDetails actor, ApiDtos.UserCreateRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        AppUser created = users.save(new AppUser(request.name().trim(), email, passwordEncoder.encode(request.password()),
                request.role(), actor.getOrganization()));
        return DtoMapper.user(created);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.UserView> supportUsers(TenantUserDetails actor) {
        return users.findAllByOrganizationIdAndRoleInOrderByNameAsc(actor.getOrganizationId(),
                        List.of(Role.ORGANIZATION_ADMIN, Role.SUPPORT_AGENT))
                .stream().map(DtoMapper::user).toList();
    }
}
