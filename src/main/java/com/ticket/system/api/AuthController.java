package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.DtoMapper;
import com.ticket.system.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final OrganizationService organizationService;

    public AuthController(AuthenticationManager authenticationManager, OrganizationService organizationService) {
        this.authenticationManager = authenticationManager;
        this.organizationService = organizationService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiDtos.LoginView> register(@Valid @RequestBody ApiDtos.OrganizationRegistration request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.register(request));
    }

    @PostMapping("/login")
    public ApiDtos.LoginView login(@Valid @RequestBody ApiDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        TenantUserDetails user = (TenantUserDetails) authentication.getPrincipal();
        return DtoMapper.login(user.getUser());
    }

    @GetMapping("/me")
    public ApiDtos.LoginView currentUser(@AuthenticationPrincipal TenantUserDetails user) {
        return DtoMapper.login(user.getUser());
    }
}
