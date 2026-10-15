package com.ticket.system.service;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.AppUser;
import com.ticket.system.model.HistoryType;
import com.ticket.system.model.Role;
import com.ticket.system.model.Ticket;
import com.ticket.system.model.TicketComment;
import com.ticket.system.model.TicketHistory;
import com.ticket.system.model.TicketPriority;
import com.ticket.system.model.TicketStatus;
import com.ticket.system.repository.TicketCommentRepository;
import com.ticket.system.repository.TicketHistoryRepository;
import com.ticket.system.repository.TicketRepository;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.TenantUserDetails;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {
    private final TicketRepository tickets;
    private final UserRepository users;
    private final TicketCommentRepository comments;
    private final TicketHistoryRepository history;

    public TicketService(TicketRepository tickets, UserRepository users,
                         TicketCommentRepository comments, TicketHistoryRepository history) {
        this.tickets = tickets;
        this.users = users;
        this.comments = comments;
        this.history = history;
    }

    @Transactional
    public ApiDtos.TicketDetail create(TenantUserDetails actor, ApiDtos.TicketCreateRequest request) {
        if (actor.getRole() == Role.CUSTOMER
                && request.requesterId() != null && !request.requesterId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers may only create tickets for themselves");
        }
        AppUser requester = request.requesterId() == null
                ? actorEntity(actor)
                : findUser(request.requesterId(), actor.getOrganizationId());
        AppUser assignee = request.assignedAgentId() == null
                ? null
                : findAssignee(request.assignedAgentId(), actor.getOrganizationId());
        if (actor.getRole() == Role.SUPPORT_AGENT) {
            if (assignee != null && assignee.getId().equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Agents cannot assign a new ticket to themselves");
            }
            if (assignee == null) {
                assignee = actorEntity(actor);
            }
        }
        Ticket ticket = tickets.save(new Ticket(actor.getOrganization(), request.subject().trim(),
                request.description().trim(), request.priority(), requester, assignee));
        if (assignee != null) {
            history.save(new TicketHistory(ticket, actorEntity(actor), HistoryType.ASSIGNMENT_CHANGED,
                    null, assignee.getEmail()));
        }
        return detail(ticket, false);
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.TicketSummary> search(TenantUserDetails actor, TicketStatus status, TicketPriority priority,
                                               Long assignedAgentId, String query, Pageable pageable) {
        Specification<Ticket> specification = (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("organization").get("id"), actor.getOrganizationId()));
            if (actor.getRole() == Role.CUSTOMER) {
                predicates.add(builder.equal(root.get("requester").get("id"), actor.getId()));
            } else if (actor.getRole() == Role.SUPPORT_AGENT) {
                predicates.add(builder.equal(root.get("assignedAgent").get("id"), actor.getId()));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (priority != null) predicates.add(builder.equal(root.get("priority"), priority));
            if (assignedAgentId != null) {
                if (actor.getRole() == Role.SUPPORT_AGENT && !assignedAgentId.equals(actor.getId())) {
                    predicates.add(builder.disjunction());
                } else {
                    predicates.add(builder.equal(root.get("assignedAgent").get("id"), assignedAgentId));
                }
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("subject")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return tickets.findAll(specification, pageable).map(DtoMapper::summary);
    }

    @Transactional(readOnly = true)
    public ApiDtos.TicketDetail get(TenantUserDetails actor, Long ticketId) {
        Ticket ticket = findTicket(ticketId, actor.getOrganizationId());
        checkTicketAccess(actor, ticket);
        return detail(ticket, actor.getRole() == Role.CUSTOMER);
    }

    @Transactional
    public ApiDtos.TicketDetail update(TenantUserDetails actor, Long ticketId, ApiDtos.TicketUpdateRequest request) {
        Ticket ticket = findTicket(ticketId, actor.getOrganizationId());
        checkTicketAccess(actor, ticket);
        if (actor.getRole() == Role.SUPPORT_AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support agents cannot edit ticket details");
        }
        ticket.update(request.subject().trim(), request.description().trim(), request.priority());
        return detail(ticket, actor.getRole() == Role.CUSTOMER);
    }

    @Transactional
    public ApiDtos.TicketDetail updateStatus(TenantUserDetails actor, Long ticketId, TicketStatus status) {
        Ticket ticket = findTicket(ticketId, actor.getOrganizationId());
        checkTicketAccess(actor, ticket);
        if (actor.getRole() == Role.CUSTOMER && status != TicketStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers may only close their own tickets");
        }
        if (ticket.getStatus() != status) {
            String oldStatus = ticket.getStatus().name();
            ticket.setStatus(status);
            history.save(new TicketHistory(ticket, actorEntity(actor), HistoryType.STATUS_CHANGED,
                    oldStatus, status.name()));
        }
        return detail(ticket, actor.getRole() == Role.CUSTOMER);
    }

    @Transactional
    public ApiDtos.TicketDetail assign(TenantUserDetails actor, Long ticketId, Long assignedAgentId) {
        Ticket ticket = findTicket(ticketId, actor.getOrganizationId());
        checkTicketAccess(actor, ticket);
        AppUser assignee = assignedAgentId == null ? null : findAssignee(assignedAgentId, actor.getOrganizationId());
        String oldValue = ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getEmail();
        String newValue = assignee == null ? null : assignee.getEmail();
        if (!java.util.Objects.equals(oldValue, newValue)) {
            ticket.setAssignedAgent(assignee);
            history.save(new TicketHistory(ticket, actorEntity(actor), HistoryType.ASSIGNMENT_CHANGED,
                    oldValue, newValue));
        }
        return detail(ticket, false);
    }

    @Transactional
    public ApiDtos.CommentView addComment(TenantUserDetails actor, Long ticketId, ApiDtos.CommentCreateRequest request) {
        Ticket ticket = findTicket(ticketId, actor.getOrganizationId());
        checkTicketAccess(actor, ticket);
        if (request.internal() && actor.getRole() == Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers cannot create internal notes");
        }
        TicketComment comment = comments.save(new TicketComment(ticket, actorEntity(actor), request.body().trim(),
                request.internal()));
        return DtoMapper.comment(comment);
    }

    private ApiDtos.TicketDetail detail(Ticket ticket, boolean customerView) {
        List<ApiDtos.CommentView> ticketComments = comments.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .filter(comment -> !customerView || !comment.isInternal())
                .map(DtoMapper::comment)
                .toList();
        List<ApiDtos.HistoryView> ticketHistory = history.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream().map(DtoMapper::history).toList();
        return new ApiDtos.TicketDetail(ticket.getId(), ticket.getSubject(), ticket.getDescription(),
                ticket.getPriority(), ticket.getStatus(), DtoMapper.user(ticket.getRequester()),
                DtoMapper.user(ticket.getAssignedAgent()), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                ticket.getClosedAt(), ticketComments, ticketHistory);
    }

    private Ticket findTicket(Long id, Long organizationId) {
        return tickets.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private AppUser findUser(Long id, Long organizationId) {
        return users.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User must belong to this organization"));
    }

    private AppUser findAssignee(Long id, Long organizationId) {
        AppUser user = findUser(id, organizationId);
        if (user.getRole() != Role.SUPPORT_AGENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must be a support agent");
        }
        return user;
    }

    private AppUser actorEntity(TenantUserDetails actor) {
        return users.findByIdAndOrganizationId(actor.getId(), actor.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void checkTicketAccess(TenantUserDetails actor, Ticket ticket) {
        if (actor.getRole() == Role.CUSTOMER && !ticket.getRequester().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        if (actor.getRole() == Role.SUPPORT_AGENT
                && (ticket.getAssignedAgent() == null || !ticket.getAssignedAgent().getId().equals(actor.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
    }

}
