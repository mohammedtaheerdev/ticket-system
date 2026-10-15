# Support Desk

A multi-tenant support ticket application built with Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, and a React frontend served by Spring Boot.

## Run locally

Requirements: Java 21+, PostgreSQL 14+, and the included Maven wrapper.

Create a PostgreSQL user and database (or use an existing database), then start the application. The local default database password is `ticket_system`:

```sh
createuser --pwprompt ticket_system
# Enter ticket_system when prompted, or change DATABASE_PASSWORD below.
createdb --owner=ticket_system ticket_system
./mvnw spring-boot:run
```

Default local connection settings are `jdbc:postgresql://localhost:5432/ticket_system`, username `ticket_system`, and password `ticket_system`. Override them with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. For example:

```sh
DATABASE_URL=jdbc:postgresql://localhost:5432/ticket_system \
DATABASE_USERNAME=myuser DATABASE_PASSWORD=mypassword ./mvnw spring-boot:run
```

The database schema is created/updated by Hibernate (`spring.jpa.hibernate.ddl-auto=update`). For production deployments, use a schema migration tool and set `ddl-auto=validate`.

Open the React UI at [http://localhost:8080](http://localhost:8080). The page loads the React runtime from unpkg, so the browser needs internet access. Create an organization in the UI to register its first Organization Admin. After signing in as that admin, select **Team** in the top bar to add Support Agents and Customers and view the organization’s user list. Emails are globally unique because they are used as login identifiers.

## Authentication and tenancy

The API uses stateless HTTP Basic authentication over HTTPS in deployed environments. The browser UI holds credentials in `sessionStorage` for the current tab and clears them on sign out. Passwords are stored with BCrypt.

Organization registration creates the organization and its first admin. Every authenticated user is associated with exactly one organization. Ticket and user lookups are organization-scoped; Organization Admins can view and manage every ticket in their organization, Customers can view and manage only tickets they requested, and Support Agents can view and manage only tickets assigned to them. Out-of-scope ticket IDs return `404`. The assigned-agent selector is available on the ticket list and ticket detail for all roles and only lists agents from the current organization. While creating a ticket, Customers and Organization Admins can assign it to any agent in their organization; Support Agents can assign it to another agent, while leaving the field unchanged automatically assigns the ticket to themselves. Customers can edit, comment on, close, and assign their own tickets to an agent. Agents can change status, comment on, and reassign tickets currently assigned to them, but cannot edit ticket details; after reassignment, a ticket no longer appears in the previous agent's work list. Customers cannot create internal notes; internal notes are visible only to organization staff.

## REST API

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Key endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an organization and its first admin |
| `POST` | `/api/auth/login` | Validate credentials and return user/organization details |
| `GET` | `/api/auth/me` | Get the authenticated identity |
| `GET`, `POST` | `/api/users` | List or create organization users (Organization Admin) |
| `GET` | `/api/support-users` | List agents/admins available for assignment |
| `GET`, `POST` | `/api/tickets` | Search/list or create tickets |
| `GET`, `PUT` | `/api/tickets/{id}` | View or update a ticket |
| `PATCH` | `/api/tickets/{id}/status` | Change status/close a ticket |
| `PATCH` | `/api/tickets/{id}/assignment` | Assign or unassign an agent |
| `POST` | `/api/tickets/{id}/comments` | Add a public comment or internal note |

Ticket search accepts `q`, `status`, `priority`, `assignedAgentId`, `page`, and `size` (maximum 100). Ticket creation accepts `subject`, `description`, `priority`, and optional `requesterId` and `assignedAgentId`. To remove an assignee, send `{"assignedAgentId":null}` to the assignment endpoint.

## Tests

```sh
./mvnw test
```

Tests use an in-memory H2 database in PostgreSQL compatibility mode; no running PostgreSQL server is needed for the test suite.
