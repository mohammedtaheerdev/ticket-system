package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.DtoMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    @GetMapping("/me")
    public ApiDtos.OrganizationView currentOrganization(@AuthenticationPrincipal TenantUserDetails user) {
        return DtoMapper.organization(user.getOrganization());
    }
}
