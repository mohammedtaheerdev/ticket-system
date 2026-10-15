package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.UserService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support-users")
@PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPPORT_AGENT', 'CUSTOMER')")
public class SupportUserController {
    private final UserService users;

    public SupportUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<ApiDtos.UserView> list(@AuthenticationPrincipal TenantUserDetails actor) {
        return users.supportUsers(actor);
    }
}
