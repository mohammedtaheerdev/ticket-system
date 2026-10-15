package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<ApiDtos.UserView> list(@AuthenticationPrincipal TenantUserDetails actor) {
        return userService.list(actor);
    }

    @PostMapping
    public ResponseEntity<ApiDtos.UserView> create(@AuthenticationPrincipal TenantUserDetails actor,
                                                   @Valid @RequestBody ApiDtos.UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(actor, request));
    }
}
