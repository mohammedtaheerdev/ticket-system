package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.security.LoginRateLimiter;
import com.ticket.system.security.OrganizationRegistrationRateLimiter;
import com.ticket.system.service.DtoMapper;
import com.ticket.system.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final OrganizationRegistrationRateLimiter registrationRateLimiter;

    public AuthController(AuthenticationManager authenticationManager, OrganizationService organizationService,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository, LoginRateLimiter loginRateLimiter,
                          OrganizationRegistrationRateLimiter registrationRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.organizationService = organizationService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    @GetMapping("/csrf")
    public ApiDtos.CsrfTokenView csrfToken(CsrfToken csrfToken) {
        return new ApiDtos.CsrfTokenView(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiDtos.LoginView> register(@Valid @RequestBody ApiDtos.OrganizationRegistration request,
                                                      HttpServletRequest servletRequest,
                                                      HttpServletResponse servletResponse) {
        registrationRateLimiter.checkAndRecord(servletRequest.getRemoteAddr());
        ApiDtos.LoginView registered = organizationService.register(request);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        establishSession(authentication, servletRequest, servletResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    @PostMapping("/login")
    public ApiDtos.LoginView login(@Valid @RequestBody ApiDtos.LoginRequest request,
                                   HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        String clientAddress = servletRequest.getRemoteAddr();
        loginRateLimiter.checkAllowed(clientAddress);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException exception) {
            loginRateLimiter.recordFailure(clientAddress);
            throw exception;
        }
        loginRateLimiter.recordSuccess(clientAddress);
        establishSession(authentication, servletRequest, servletResponse);
        TenantUserDetails user = (TenantUserDetails) authentication.getPrincipal();
        return DtoMapper.login(user.getUser());
    }

    private void establishSession(Authentication authentication, HttpServletRequest servletRequest,
                                  HttpServletResponse servletResponse) {
        var existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public ApiDtos.LoginView currentUser(@AuthenticationPrincipal TenantUserDetails user) {
        return DtoMapper.login(user.getUser());
    }
}
