package com.ticket.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.model.AppUser;
import com.ticket.system.model.Organization;
import com.ticket.system.model.Role;
import com.ticket.system.model.TicketPriority;
import com.ticket.system.model.TicketStatus;
import com.ticket.system.repository.OrganizationRepository;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.TenantUserDetails;
import com.ticket.system.service.TicketService;
import com.ticket.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceTests {
    @Autowired private OrganizationRepository organizations;
    @Autowired private UserRepository users;
    @Autowired private TicketService tickets;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void customerCannotReadTicketFromAnotherOrganization() {
        Organization firstOrg = organizations.save(new Organization("Northwind"));
        Organization secondOrg = organizations.save(new Organization("Contoso"));
        AppUser firstCustomer = user("first@example.test", Role.CUSTOMER, firstOrg);
        AppUser secondCustomer = user("second@example.test", Role.CUSTOMER, secondOrg);

        ApiDtos.TicketDetail otherTenantTicket = tickets.create(new TenantUserDetails(secondCustomer),
                new ApiDtos.TicketCreateRequest("Private issue", "Details", TicketPriority.MEDIUM, null, null));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tickets.get(new TenantUserDetails(firstCustomer), otherTenantTicket.id()));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void customerDoesNotSeeInternalNotes() {
        Organization organization = organizations.save(new Organization("Fabrikam"));
        AppUser customer = user("requester@example.test", Role.CUSTOMER, organization);
        AppUser admin = user("admin4@example.test", Role.ORGANIZATION_ADMIN, organization);
        AppUser agent = user("agent@example.test", Role.SUPPORT_AGENT, organization);
        TenantUserDetails customerPrincipal = new TenantUserDetails(customer);
        ApiDtos.TicketDetail created = tickets.create(new TenantUserDetails(admin),
                new ApiDtos.TicketCreateRequest("Cannot sign in", "Sign in page loops", TicketPriority.HIGH,
                        customer.getId(), agent.getId()));

        tickets.addComment(new TenantUserDetails(agent), created.id(),
                new ApiDtos.CommentCreateRequest("Investigating internally", true));
        tickets.addComment(new TenantUserDetails(agent), created.id(),
                new ApiDtos.CommentCreateRequest("We are looking into this", false));

        ApiDtos.TicketDetail visible = tickets.get(customerPrincipal, created.id());
        assertEquals(1, visible.comments().size());
        assertEquals("We are looking into this", visible.comments().getFirst().body());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> tickets.addComment(customerPrincipal, created.id(),
                        new ApiDtos.CommentCreateRequest("Not allowed", true))).getStatusCode().value());
    }

    @Test
    void customerCloseReturnsClosedTicketStatus() {
        Organization organization = organizations.save(new Organization("Woodgrove Bank"));
        AppUser customer = user("closer@example.test", Role.CUSTOMER, organization);
        TenantUserDetails customerPrincipal = new TenantUserDetails(customer);
        ApiDtos.TicketDetail created = tickets.create(customerPrincipal,
                new ApiDtos.TicketCreateRequest("Close this issue", "Issue resolved", TicketPriority.LOW, null, null));

        ApiDtos.TicketDetail response = tickets.updateStatus(customerPrincipal, created.id(), TicketStatus.CLOSED);

        assertEquals(TicketStatus.CLOSED, response.status());
        assertEquals("CLOSED", response.history().getFirst().newValue());
        assertEquals(response.status(), tickets.get(customerPrincipal, created.id()).status());
    }

    @Test
    void statusAndAssignmentChangesAreRecorded() {
        Organization organization = organizations.save(new Organization("Adventure Works"));
        AppUser requester = user("customer@example.test", Role.CUSTOMER, organization);
        AppUser admin = user("admin5@example.test", Role.ORGANIZATION_ADMIN, organization);
        AppUser secondAgent = user("agent2@example.test", Role.SUPPORT_AGENT, organization);
        TenantUserDetails staff = new TenantUserDetails(admin);
        ApiDtos.TicketDetail created = tickets.create(staff,
                new ApiDtos.TicketCreateRequest("Service request", "Need assistance", TicketPriority.LOW,
                        requester.getId(), null));

        tickets.updateStatus(staff, created.id(), TicketStatus.IN_PROGRESS);
        ApiDtos.TicketDetail updated = tickets.assign(staff, created.id(), secondAgent.getId());

        assertEquals(TicketStatus.IN_PROGRESS, updated.status());
        assertEquals(secondAgent.getId(), updated.assignedAgent().id());
        assertEquals(2, updated.history().size());
        assertEquals("OPEN", updated.history().getFirst().oldValue());
        assertEquals("agent2@example.test", updated.history().getLast().newValue());
    }

    @Test
    void organizationAdminCanCreateAgentsAndCustomersInTheirOrganization() {
        Organization organization = organizations.save(new Organization("Tailspin Toys"));
        Organization anotherOrganization = organizations.save(new Organization("Wide World Importers"));
        AppUser admin = user("admin@example.test", Role.ORGANIZATION_ADMIN, organization);
        user("other-admin@example.test", Role.ORGANIZATION_ADMIN, anotherOrganization);
        TenantUserDetails adminPrincipal = new TenantUserDetails(admin);

        ApiDtos.UserView agent = userService.create(adminPrincipal,
                new ApiDtos.UserCreateRequest("Support Agent", "new-agent@example.test",
                        "agent-password-123", Role.SUPPORT_AGENT));
        ApiDtos.UserView customer = userService.create(adminPrincipal,
                new ApiDtos.UserCreateRequest("Customer", "new-customer@example.test",
                        "customer-password-123", Role.CUSTOMER));

        assertEquals(Role.SUPPORT_AGENT, agent.role());
        assertEquals(Role.CUSTOMER, customer.role());
        assertEquals(3, userService.list(adminPrincipal).size());
        assertEquals(1, userService.supportUsers(adminPrincipal).stream()
                .filter(user -> user.role() == Role.SUPPORT_AGENT).count());
        assertEquals(organization.getId(), users.findByEmailIgnoreCase(agent.email()).orElseThrow()
                .getOrganization().getId());
    }

    @Test
    void adminsSeeOrganizationTicketsCustomersSeeOwnAndAgentsSeeAssigned() {
        Organization organization = organizations.save(new Organization("Proseware"));
        Organization otherOrganization = organizations.save(new Organization("Lucerne Publishing"));
        AppUser admin = user("admin1@example.test", Role.ORGANIZATION_ADMIN, organization);
        AppUser otherAdmin = user("admin3@example.test", Role.ORGANIZATION_ADMIN, otherOrganization);
        AppUser customer = user("customer1@example.test", Role.CUSTOMER, organization);
        AppUser anotherCustomer = user("customer2@example.test", Role.CUSTOMER, organization);
        AppUser agent = user("agent3@example.test", Role.SUPPORT_AGENT, organization);
        AppUser otherAgent = user("agent4@example.test", Role.SUPPORT_AGENT, organization);
        AppUser externalCustomer = user("external@example.test", Role.CUSTOMER, otherOrganization);

        ApiDtos.TicketDetail assignedToAgent = tickets.create(new TenantUserDetails(admin),
                new ApiDtos.TicketCreateRequest("Assigned ticket", "Agent-owned", TicketPriority.HIGH,
                        customer.getId(), agent.getId()));
        ApiDtos.TicketDetail assignedToOther = tickets.create(new TenantUserDetails(admin),
                new ApiDtos.TicketCreateRequest("Other agent ticket", "Not visible to first agent",
                        TicketPriority.MEDIUM, anotherCustomer.getId(), otherAgent.getId()));
        ApiDtos.TicketDetail unassigned = tickets.create(new TenantUserDetails(admin),
                new ApiDtos.TicketCreateRequest("Unassigned ticket", "Admin only", TicketPriority.LOW,
                        anotherCustomer.getId(), null));
        tickets.create(new TenantUserDetails(otherAdmin),
                new ApiDtos.TicketCreateRequest("Other organization ticket", "Not in tenant",
                        TicketPriority.LOW, externalCustomer.getId(), null));

        var pageable = PageRequest.of(0, 20);
        assertEquals(3, tickets.search(new TenantUserDetails(admin), null, null, null, null, pageable)
                .getTotalElements());
        assertEquals(1, tickets.search(new TenantUserDetails(customer), null, null, null, null, pageable)
                .getTotalElements());
        assertEquals(1, tickets.search(new TenantUserDetails(agent), null, null, null, null, pageable)
                .getTotalElements());
        assertEquals(assignedToAgent.id(), tickets.search(new TenantUserDetails(agent), null, null,
                null, null, pageable).getContent().getFirst().id());
        assertEquals(0, tickets.search(new TenantUserDetails(agent), null, null,
                otherAgent.getId(), null, pageable).getTotalElements());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tickets.get(new TenantUserDetails(customer), assignedToOther.id())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tickets.get(new TenantUserDetails(agent), assignedToOther.id())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tickets.get(new TenantUserDetails(agent), unassigned.id())).getStatusCode());
    }

    @Test
    void requestersCanAssignOwnTicketsAndAgentsCanReassignAssignedTickets() {
        Organization organization = organizations.save(new Organization("Northwind Traders"));
        AppUser admin = user("admin2@example.test", Role.ORGANIZATION_ADMIN, organization);
        AppUser customer = user("customer3@example.test", Role.CUSTOMER, organization);
        AppUser agent = user("agent5@example.test", Role.SUPPORT_AGENT, organization);
        AppUser otherAgent = user("agent6@example.test", Role.SUPPORT_AGENT, organization);
        ApiDtos.TicketDetail created = tickets.create(new TenantUserDetails(admin),
                new ApiDtos.TicketCreateRequest("Initial subject", "Initial details", TicketPriority.LOW,
                        customer.getId(), agent.getId()));

        ApiDtos.TicketDetail customerUpdate = tickets.update(new TenantUserDetails(customer), created.id(),
                new ApiDtos.TicketUpdateRequest("Customer update", "Updated details", TicketPriority.MEDIUM));
        TenantUserDetails agentPrincipal = new TenantUserDetails(agent);
        ResponseStatusException agentEditDenied = assertThrows(ResponseStatusException.class,
                () -> tickets.update(agentPrincipal, created.id(),
                        new ApiDtos.TicketUpdateRequest("Agent update", "Agent details", TicketPriority.HIGH)));
        ApiDtos.TicketDetail agentStatusUpdate = tickets.updateStatus(agentPrincipal, created.id(),
                TicketStatus.IN_PROGRESS);
        tickets.addComment(agentPrincipal, created.id(),
                new ApiDtos.CommentCreateRequest("Working on this ticket", true));
        tickets.updateStatus(agentPrincipal, created.id(), TicketStatus.CLOSED);

        assertEquals("Customer update", customerUpdate.subject());
        assertEquals(HttpStatus.FORBIDDEN, agentEditDenied.getStatusCode());
        assertEquals(TicketStatus.IN_PROGRESS, agentStatusUpdate.status());
        tickets.updateStatus(agentPrincipal, created.id(), TicketStatus.CLOSED);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> tickets.update(agentPrincipal, created.id(),
                        new ApiDtos.TicketUpdateRequest("Closed edit", "Not permitted", TicketPriority.URGENT)))
                .getStatusCode());
        ApiDtos.TicketDetail agentAssigned = tickets.assign(agentPrincipal, created.id(), otherAgent.getId());
        assertEquals(otherAgent.getId(), agentAssigned.assignedAgent().id());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> tickets.get(agentPrincipal, created.id())).getStatusCode());

        ApiDtos.TicketDetail customerAssigned = tickets.assign(new TenantUserDetails(customer), created.id(),
                agent.getId());
        assertEquals(agent.getId(), customerAssigned.assignedAgent().id());
    }

    @Test
    void agentCreatedTicketIsAutomaticallyAssignedToThatAgent() {
        Organization organization = organizations.save(new Organization("Contoso"));
        AppUser agent = user("agent7@example.test", Role.SUPPORT_AGENT, organization);
        AppUser otherAgent = user("agent8@example.test", Role.SUPPORT_AGENT, organization);

        ApiDtos.TicketDetail ticket = tickets.create(new TenantUserDetails(agent),
                new ApiDtos.TicketCreateRequest("Agent-created request", "Request details",
                        TicketPriority.MEDIUM, null, null));
        ApiDtos.TicketDetail assignedToOtherAgent = tickets.create(new TenantUserDetails(agent),
                new ApiDtos.TicketCreateRequest("Delegated request", "Assigned to a teammate",
                        TicketPriority.MEDIUM, null, otherAgent.getId()));

        assertEquals(agent.getId(), ticket.assignedAgent().id());
        assertEquals(ticket.id(), tickets.search(new TenantUserDetails(agent), null, null,
                null, null, PageRequest.of(0, 10)).getContent().getFirst().id());
        assertEquals(otherAgent.getId(), assignedToOtherAgent.assignedAgent().id());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> tickets.create(new TenantUserDetails(agent),
                        new ApiDtos.TicketCreateRequest("Self-assigned request", "Details", TicketPriority.LOW,
                                null, agent.getId()))).getStatusCode());
    }

    @Test
    void customerCanAssignNewTicketToAnAgentInTheirOrganization() {
        Organization organization = organizations.save(new Organization("Northwind"));
        Organization otherOrganization = organizations.save(new Organization("Fabrikam"));
        AppUser customer = user("customer-create@example.test", Role.CUSTOMER, organization);
        AppUser agent = user("agent-create@example.test", Role.SUPPORT_AGENT, organization);
        AppUser externalAgent = user("external-agent@example.test", Role.SUPPORT_AGENT, otherOrganization);

        ApiDtos.TicketDetail ticket = tickets.create(new TenantUserDetails(customer),
                new ApiDtos.TicketCreateRequest("Customer request", "Please help", TicketPriority.HIGH,
                        null, agent.getId()));

        assertEquals(customer.getId(), ticket.requester().id());
        assertEquals(agent.getId(), ticket.assignedAgent().id());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> tickets.create(new TenantUserDetails(customer),
                        new ApiDtos.TicketCreateRequest("Invalid assignment", "Cross-tenant", TicketPriority.LOW,
                                null, externalAgent.getId()))).getStatusCode());
    }

    private AppUser user(String email, Role role, Organization organization) {
        return users.save(new AppUser(email.substring(0, email.indexOf('@')), email,
                passwordEncoder.encode("test-password-123"), role, organization));
    }
}
