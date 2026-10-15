package com.ticket.system.api;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.TicketPriority;
import com.ticket.system.model.TicketStatus;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public Page<ApiDtos.TicketSummary> search(
            @AuthenticationPrincipal TenantUserDetails actor,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable safePage = PageRequest.of(page, size,
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));
        return ticketService.search(actor, status, priority, assignedAgentId, q, safePage);
    }

    @PostMapping
    public ResponseEntity<ApiDtos.TicketDetail> create(@AuthenticationPrincipal TenantUserDetails actor,
                                                       @Valid @RequestBody ApiDtos.TicketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(actor, request));
    }

    @GetMapping("/{id}")
    public ApiDtos.TicketDetail get(@AuthenticationPrincipal TenantUserDetails actor, @PathVariable Long id) {
        return ticketService.get(actor, id);
    }

    @PutMapping("/{id}")
    public ApiDtos.TicketDetail update(@AuthenticationPrincipal TenantUserDetails actor, @PathVariable Long id,
                                       @Valid @RequestBody ApiDtos.TicketUpdateRequest request) {
        return ticketService.update(actor, id, request);
    }

    @PatchMapping("/{id}/status")
    public ApiDtos.TicketDetail updateStatus(@AuthenticationPrincipal TenantUserDetails actor, @PathVariable Long id,
                                              @Valid @RequestBody ApiDtos.StatusUpdateRequest request) {
        return ticketService.updateStatus(actor, id, request.status());
    }

    @PatchMapping("/{id}/assignment")
    public ApiDtos.TicketDetail assign(@AuthenticationPrincipal TenantUserDetails actor, @PathVariable Long id,
                                        @Valid @RequestBody ApiDtos.AssignmentUpdateRequest request) {
        return ticketService.assign(actor, id, request.assignedAgentId());
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiDtos.CommentView> comment(@AuthenticationPrincipal TenantUserDetails actor, @PathVariable Long id,
                                                       @Valid @RequestBody ApiDtos.CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addComment(actor, id, request));
    }
}
