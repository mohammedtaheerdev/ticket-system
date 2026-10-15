package com.ticket.system.service;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.AppUser;
import com.ticket.system.model.Organization;
import com.ticket.system.model.Ticket;
import com.ticket.system.model.TicketComment;
import com.ticket.system.model.TicketHistory;

public final class DtoMapper {
    private DtoMapper() {
    }

    public static ApiDtos.UserView user(AppUser user) {
        return user == null ? null : new ApiDtos.UserView(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public static ApiDtos.OrganizationView organization(Organization organization) {
        return new ApiDtos.OrganizationView(organization.getId(), organization.getName(), organization.getCreatedAt());
    }

    public static ApiDtos.LoginView login(AppUser user) {
        return new ApiDtos.LoginView(user(user), organization(user.getOrganization()));
    }

    public static ApiDtos.TicketSummary summary(Ticket ticket) {
        return new ApiDtos.TicketSummary(ticket.getId(), ticket.getSubject(), ticket.getPriority(), ticket.getStatus(),
                user(ticket.getRequester()), user(ticket.getAssignedAgent()), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), ticket.getClosedAt());
    }

    public static ApiDtos.CommentView comment(TicketComment comment) {
        return new ApiDtos.CommentView(comment.getId(), user(comment.getAuthor()), comment.getBody(),
                comment.isInternal(), comment.getCreatedAt());
    }

    public static ApiDtos.HistoryView history(TicketHistory history) {
        return new ApiDtos.HistoryView(history.getId(), user(history.getChangedBy()), history.getType(),
                history.getOldValue(), history.getNewValue(), history.getCreatedAt());
    }
}
