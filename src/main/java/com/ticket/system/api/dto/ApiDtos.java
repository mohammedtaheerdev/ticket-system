package com.ticket.system.api.dto;

import com.ticket.system.model.HistoryType;
import com.ticket.system.model.Role;
import com.ticket.system.model.TicketPriority;
import com.ticket.system.model.TicketStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record OrganizationRegistration(
            @NotBlank @Size(max = 120) String organizationName,
            @NotBlank @Size(max = 120) String adminName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 10, max = 100) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserCreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotNull Role role) {
    }

    public record TicketCreateRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 10000) String description,
            @NotNull TicketPriority priority,
            Long requesterId,
            Long assignedAgentId) {
    }

    public record TicketUpdateRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 10000) String description,
            @NotNull TicketPriority priority) {
    }

    public record StatusUpdateRequest(@NotNull TicketStatus status) {
    }

    public record AssignmentUpdateRequest(Long assignedAgentId) {
    }

    public record CommentCreateRequest(@NotBlank @Size(max = 10000) String body, boolean internal) {
    }

    public record UserView(Long id, String name, String email, Role role) {
    }

    public record OrganizationView(Long id, String name, Instant createdAt) {
    }

    public record LoginView(UserView user, OrganizationView organization) {
    }

    public record CsrfTokenView(String token, String headerName) {
    }

    public record CommentView(Long id, UserView author, String body, boolean internal, Instant createdAt) {
    }

    public record HistoryView(
            Long id, UserView changedBy, HistoryType type, String oldValue, String newValue, Instant createdAt) {
    }

    public record TicketSummary(
            Long id, String subject, TicketPriority priority, TicketStatus status, UserView requester,
            UserView assignedAgent, Instant createdAt, Instant updatedAt, Instant closedAt) {
    }

    public record TicketDetail(
            Long id, String subject, String description, TicketPriority priority, TicketStatus status,
            UserView requester, UserView assignedAgent, Instant createdAt, Instant updatedAt, Instant closedAt,
            List<CommentView> comments, List<HistoryView> history) {
    }
}
