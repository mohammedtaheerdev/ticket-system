(() => {
  const { createElement: h, useEffect, useState } = React;
  const roles = { ORGANIZATION_ADMIN: "Organization Admin", SUPPORT_AGENT: "Support Agent", CUSTOMER: "Customer" };
  const authKey = "support-desk-auth";

  async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    const credentials = sessionStorage.getItem(authKey);
    if (credentials) headers.Authorization = `Basic ${credentials}`;
    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.message || `Request failed (${response.status})`);
    }
    return response.status === 204 ? null : response.json();
  }

  function App() {
    const [identity, setIdentity] = useState(null);
    const [registering, setRegistering] = useState(false);
    const [error, setError] = useState("");
    const [tickets, setTickets] = useState([]);
    const [filters, setFilters] = useState({ status: "", priority: "", q: "" });
    const [page, setPage] = useState(0);
    const [pageCount, setPageCount] = useState(0);
    const [ticket, setTicket] = useState(null);
    const [editingTicket, setEditingTicket] = useState(false);
    const [creating, setCreating] = useState(false);
    const [supportUsers, setSupportUsers] = useState([]);
    const [teamUsers, setTeamUsers] = useState([]);
    const [showTeam, setShowTeam] = useState(false);
    const [refresh, setRefresh] = useState(0);

    useEffect(() => {
      if (!sessionStorage.getItem(authKey)) return;
      api("/api/auth/me").then(setIdentity).catch(() => sessionStorage.removeItem(authKey));
    }, []);

    useEffect(() => {
      if (!identity || ticket || creating || showTeam) return;
      const params = new URLSearchParams({ page: String(page), size: "15" });
      Object.entries(filters).forEach(([key, value]) => value && params.set(key, value));
      api(`/api/tickets?${params}`)
        .then(result => { setTickets(result.content); setPageCount(result.totalPages); })
        .catch(reason => setError(reason.message));
    }, [identity, filters, page, refresh, ticket, creating, showTeam]);

    useEffect(() => {
      if (identity) {
        api("/api/support-users")
          .then(setSupportUsers)
          .catch(reason => setError(`Could not load agents: ${reason.message}`));
      }
    }, [identity, refresh, ticket, showTeam]);

    useEffect(() => {
      if (identity?.user.role === "ORGANIZATION_ADMIN" && showTeam) {
        api("/api/users").then(setTeamUsers).catch(reason => setError(reason.message));
      }
    }, [identity, showTeam]);

    async function login(event) {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const email = data.get("email");
      const password = data.get("password");
      try {
        await api("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
        sessionStorage.setItem(authKey, btoa(`${email}:${password}`));
        setTicket(null);
        setEditingTicket(false);
        setCreating(false);
        setShowTeam(false);
        setPage(0);
        setIdentity(await api("/api/auth/me"));
        setError("");
      } catch (reason) { setError(reason.message); }
    }

    async function register(event) {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const fields = Object.fromEntries(data.entries());
      try {
        const result = await api("/api/auth/register", { method: "POST", body: JSON.stringify(fields) });
        sessionStorage.setItem(authKey, btoa(`${fields.email}:${fields.password}`));
        setTicket(null);
        setEditingTicket(false);
        setCreating(false);
        setShowTeam(false);
        setPage(0);
        setIdentity(result);
        setError("");
      } catch (reason) { setError(reason.message); }
    }

    function logout() {
      sessionStorage.removeItem(authKey);
      setIdentity(null);
      setTicket(null);
      setTickets([]);
      setSupportUsers([]);
      setTeamUsers([]);
      setFilters({ status: "", priority: "", q: "", assignedAgentId: "" });
      setPage(0);
      setShowTeam(false);
      setEditingTicket(false);
      setCreating(false);
      setError("");
    }

    function loadTicket(id) {
      api(`/api/tickets/${id}`).then(setTicket).catch(reason => setError(reason.message));
    }

    async function updateStatus(status) {
      try {
        const result = await api(`/api/tickets/${ticket.id}/status`, {
          method: "PATCH", body: JSON.stringify({ status })
        });
        setTicket(result); setRefresh(value => value + 1); setError("");
      } catch (reason) { setError(reason.message); }
    }

    async function updateTicket(event) {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      try {
        const result = await api(`/api/tickets/${ticket.id}`, {
          method: "PUT",
          body: JSON.stringify({
            subject: data.get("subject"),
            description: data.get("description"),
            priority: data.get("priority")
          })
        });
        setTicket(result);
        setEditingTicket(false);
        setRefresh(value => value + 1);
        setError("");
      } catch (reason) { setError(reason.message); }
    }

    async function assign(ticketId, agentId) {
      try {
        const result = await api(`/api/tickets/${ticketId}/assignment`, {
          method: "PATCH", body: JSON.stringify({ assignedAgentId: agentId ? Number(agentId) : null })
        });
        if (ticket?.id === ticketId) {
          if (identity.user.role === "SUPPORT_AGENT" && Number(agentId) !== identity.user.id) {
            setTicket(null);
          } else {
            setTicket(result);
          }
        }
        setRefresh(value => value + 1); setError("");
      } catch (reason) { setError(reason.message); }
    }

    async function addComment(event) {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      try {
        await api(`/api/tickets/${ticket.id}/comments`, {
          method: "POST",
          body: JSON.stringify({ body: data.get("body"), internal: data.get("internal") === "on" })
        });
        form.reset();
        loadTicket(ticket.id);
      } catch (reason) { setError(reason.message); }
    }

    async function createTicket(event) {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const payload = {
        subject: data.get("subject"), description: data.get("description"),
        priority: data.get("priority") || "MEDIUM"
      };
      if (data.get("assignedAgentId")) payload.assignedAgentId = Number(data.get("assignedAgentId"));
      try {
        const created = await api("/api/tickets", { method: "POST", body: JSON.stringify(payload) });
        const assignedToAnotherAgent = identity.user.role === "SUPPORT_AGENT"
          && payload.assignedAgentId && payload.assignedAgentId !== identity.user.id;
        setCreating(false);
        setTicket(assignedToAnotherAgent ? null : created);
        setRefresh(value => value + 1);
        setError("");
      } catch (reason) { setError(reason.message); }
    }

    async function createUser(event) {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      const payload = Object.fromEntries(data.entries());
      try {
        await api("/api/users", { method: "POST", body: JSON.stringify(payload) });
        setTeamUsers(await api("/api/users"));
        setRefresh(value => value + 1);
        setError("");
        form.reset();
      } catch (reason) { setError(reason.message); }
    }

    if (!identity) {
      return h("main", { className: "auth-shell" },
        h("section", { className: "auth-card" },
          h("div", { className: "brand-mark" }, "SD"),
          h("h1", null, registering ? "Create your organization" : "Welcome back"),
          h("p", { className: "muted" }, registering ? "Start a private support workspace." : "Sign in to your support workspace."),
          error && h("div", { className: "alert" }, error),
          h("form", { onSubmit: registering ? register : login },
            registering && h(React.Fragment, null,
              field("Organization name", "organizationName", "text", true),
              field("Your name", "adminName", "text", true)),
            field("Email", "email", "email", true),
            field("Password", "password", "password", true),
            registering && h("small", { className: "muted hint" }, "Use at least 10 characters."),
            h("button", { className: "primary full", type: "submit" }, registering ? "Create workspace" : "Sign in")),
          h("button", { className: "text-button", onClick: () => { setRegistering(!registering); setError(""); } },
            registering ? "Already have a workspace? Sign in" : "New here? Create an organization")));
    }

    const isStaff = identity.user.role !== "CUSTOMER";
    const isAdmin = identity.user.role === "ORGANIZATION_ADMIN";
    return h("div", { className: "app-shell" },
      h("header", { className: "topbar" },
        h("a", { className: "brand", href: "#", onClick: event => { event.preventDefault(); setTicket(null); setCreating(false); } },
          h("span", { className: "brand-mark small" }, "SD"), h("span", null, "Support Desk")),
        h("div", { className: "account" },
          h("span", { className: "org-label" }, identity.organization.name),
          h("span", { className: "avatar" }, identity.user.name.slice(0, 1).toUpperCase()),
          h("span", { className: "account-name" }, identity.user.name),
          identity.user.role === "ORGANIZATION_ADMIN" && h("button", {
            className: "text-button", onClick: () => {
              setTicket(null); setCreating(false); setShowTeam(!showTeam); setError("");
            }
          }, showTeam ? "Tickets" : "Team"),
          h("button", { className: "text-button", onClick: logout }, "Sign out"))),
      h("main", { className: "content" },
        h("div", { className: "page-heading" },
          h("div", null, h("p", { className: "eyebrow" }, roles[identity.user.role]),
            h("h1", null, ticket ? `Ticket #${ticket.id}` : creating ? "New ticket" : showTeam ? "Team" :
              identity.user.role === "SUPPORT_AGENT" ? "My assigned tickets" :
                identity.user.role === "CUSTOMER" ? "My tickets" : "Organization tickets")),
          !ticket && !showTeam && h("button", { className: "primary", onClick: () => { setCreating(true); setError(""); } }, "+ New ticket")),
        error && h("div", { className: "alert inline-alert" }, error),
        ticket ? ticketDetail(ticket, isStaff, identity.user.role !== "SUPPORT_AGENT",
          supportUsers, updateTicket, editingTicket,
          setEditingTicket, updateStatus, assign, addComment,
          () => { setTicket(null); setError(""); }) :
          creating ? createForm(supportUsers, identity.user, createTicket, () => setCreating(false)) :
          showTeam ? teamManagement(teamUsers, createUser) :
          ticketList(tickets, filters, setFilters, page, pageCount, setPage, loadTicket, isStaff,
            identity.user,
            supportUsers, assign)));
  }

  function field(label, name, type = "text", required = false) {
    return h("label", { className: "field" }, h("span", null, label),
      h("input", { name, type, required, minLength: name === "password" ? 10 : undefined,
        autoComplete: name === "password" ? "current-password" : name === "email" ? "email" : "off" }));
  }

  function ticketList(tickets, filters, setFilters, page, pageCount, setPage, open, isStaff,
                      actor, supportUsers, assign) {
    return h(React.Fragment, null,
      h("section", { className: "filters" },
        h("input", { placeholder: "Search tickets…", value: filters.q,
          onChange: event => { setPage(0); setFilters({ ...filters, q: event.target.value }); } }),
        selectFilter("Any status", ["OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"],
          filters.status, value => { setPage(0); setFilters({ ...filters, status: value }); }),
        selectFilter("Any priority", ["LOW", "MEDIUM", "HIGH", "URGENT"],
          filters.priority, value => { setPage(0); setFilters({ ...filters, priority: value }); }),
        actor.role !== "SUPPORT_AGENT" && h("select", { value: filters.assignedAgentId || "",
          "aria-label": "Filter tickets by assigned agent",
          onChange: event => { setPage(0); setFilters({ ...filters, assignedAgentId: event.target.value }); } },
          h("option", { value: "" }, actor.role === "SUPPORT_AGENT" ? "My assigned agent" : "Any agent"),
          supportUsers.filter(user => user.role === "SUPPORT_AGENT" && (
            actor.role !== "SUPPORT_AGENT" || user.id === actor.id)).length === 0 &&
            h("option", { disabled: true }, "No agents in organization"),
          supportUsers.filter(user => user.role === "SUPPORT_AGENT" &&
            (actor.role !== "SUPPORT_AGENT" || user.id === actor.id))
            .map(user => h("option", { key: user.id, value: user.id }, user.name)))),
      h("section", { className: "ticket-table" },
        h("div", { className: "table-heading" }, h("span", { className: "ticket-number-heading" }, "No."),
          h("span", null, "Ticket"),
          h("span", null, "Status"), h("span", null, "Priority"),
          h("span", null, isStaff ? "Requester" : "Created"),
          h("span", null, "Assigned agent")),
        tickets.length ? tickets.map((item, index) => h("div", { className: "ticket-row", key: item.id },
          h("span", { className: "ticket-number" }, String(page * 15 + index + 1)),
          h("button", { className: "ticket-title ticket-open-button", onClick: () => open(item.id) },
            h("strong", null, item.subject),
            h("small", null, `#${item.id} · ${isStaff ? item.requester.name : item.createdAt.slice(0, 10)}`)),
          h("span", null, badge(item.status)),
          h("span", { className: `priority priority-${item.priority.toLowerCase()}` }, item.priority),
          h("span", { className: "table-person" }, isStaff ? item.requester.name : item.createdAt.slice(0, 10)),
          agentSelect(item.id, item.assignedAgent?.id, supportUsers, assign))) :
          h("div", { className: "empty-state" }, h("div", { className: "empty-icon" }, "✓"),
            h("strong", null, "No tickets found"), h("p", null, "Try changing your filters or create a ticket."))),
      h("div", { className: "pagination" },
        h("span", { className: "muted" }, pageCount ? `Page ${page + 1} of ${pageCount}` : "No pages"),
        h("button", { disabled: page <= 0, onClick: () => setPage(page - 1) }, "Previous"),
        h("button", { disabled: page + 1 >= pageCount, onClick: () => setPage(page + 1) }, "Next")));
  }

  function ticketDetail(ticket, isStaff, canEditTicket, supportUsers, updateTicket, editingTicket, setEditingTicket,
                        updateStatus, assign, addComment, back) {
    const availableStatuses = isStaff
      ? ["OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"]
      : [...new Set([ticket.status, "CLOSED"])];
    const statusControl = h("label", { className: "field status-control" }, h("span", null, "Update status"),
      h("select", { value: ticket.status, onChange: event => updateStatus(event.target.value) },
        availableStatuses.map(status => h("option", { key: status, value: status },
          status.replaceAll("_", " ")))));
    return h("section", { className: "detail-layout" },
      h("div", { className: "detail-main" },
        h("button", { className: "back-link", onClick: back }, "← All tickets"),
        h("div", { className: "detail-card" },
          h("div", { className: "ticket-meta" }, badge(ticket.status), h("span", null, `Opened ${formatDate(ticket.createdAt)}`)),
          canEditTicket && editingTicket ? h("form", { className: "ticket-edit-form", onSubmit: updateTicket },
            h("label", { className: "field" }, h("span", null, "Subject"),
              h("input", { name: "subject", maxLength: 200, defaultValue: ticket.subject, required: true })),
            h("label", { className: "field" }, h("span", null, "Description"),
              h("textarea", { name: "description", maxLength: 10000, rows: 6, defaultValue: ticket.description, required: true })),
            h("label", { className: "field" }, h("span", null, "Priority"),
              h("select", { name: "priority", defaultValue: ticket.priority },
                ["LOW", "MEDIUM", "HIGH", "URGENT"].map(value => h("option", { key: value, value }, value)))),
            h("div", { className: "form-actions" },
              h("button", { type: "button", onClick: () => setEditingTicket(false) }, "Cancel"),
              h("button", { className: "primary", type: "submit" }, "Save changes"))) :
            h(React.Fragment, null,
              h("h2", null, ticket.subject),
              h("p", { className: "description" }, ticket.description),
              canEditTicket && h("button", { className: "text-button edit-ticket-button",
                onClick: () => setEditingTicket(true) }, "Edit ticket")),
          h("hr"),
          h("h3", null, `Conversation (${ticket.comments.length})`),
          ticket.comments.length ? ticket.comments.map(comment => h("article", { className: "comment", key: comment.id },
            h("div", { className: "comment-avatar" }, comment.author.name.slice(0, 1).toUpperCase()),
            h("div", { className: "comment-content" },
              h("div", { className: "comment-heading" }, h("strong", null, comment.author.name),
                comment.internal && h("span", { className: "internal-tag" }, "Internal note"),
                h("time", null, formatDate(comment.createdAt))),
              h("p", null, comment.body)))) : h("p", { className: "muted" }, "No comments yet."),
          h("form", { className: "comment-form", onSubmit: addComment },
            h("textarea", { name: "body", placeholder: "Write a reply…", required: true, rows: 3 }),
            h("div", { className: "form-actions" },
              isStaff && h("label", { className: "checkbox" }, h("input", { type: "checkbox", name: "internal" }), "Internal note"),
              h("button", { className: "primary", type: "submit" }, "Add comment"))))),
      h("aside", { className: "side-card" },
        h("h3", null, "Ticket details"),
        h("div", { className: "side-field" }, h("span", null, "Priority"),
          h("strong", { className: `priority priority-${ticket.priority.toLowerCase()}` }, ticket.priority)),
        h("div", { className: "side-field" }, h("span", null, "Requester"), h("strong", null, ticket.requester.name)),
        h("div", { className: "side-field" }, h("span", null, "Assigned agent"),
          agentSelect(ticket.id, ticket.assignedAgent?.id, supportUsers, assign)),
        h("div", { className: "side-field" }, h("span", null, "Updated"), h("strong", null, formatDate(ticket.updatedAt))),
        h("div", { className: "side-field" }, h("span", null, "History"),
          ticket.history.length ? ticket.history.map(entry =>
            h("p", { className: "history-entry", key: entry.id },
              `${entry.type === "STATUS_CHANGED" ? "Status" : "Assignee"}: ${entry.oldValue || "—"} → ${entry.newValue || "—"}`)) :
            h("small", { className: "muted" }, "No changes yet.")),
        statusControl));
  }

  function createForm(supportUsers, actor, submit, cancel) {
    const agents = supportUsers.filter(user => user.role === "SUPPORT_AGENT"
      && (actor.role !== "SUPPORT_AGENT" || user.id !== actor.id));
    return h("form", { className: "create-card", onSubmit: submit },
      h("label", { className: "field" }, h("span", null, "Subject"),
        h("input", { name: "subject", maxLength: 200, required: true, placeholder: "Briefly describe the issue" })),
      h("label", { className: "field" }, h("span", null, "Description"),
        h("textarea", { name: "description", rows: 7, maxLength: 10000, required: true, placeholder: "Add details to help us investigate…" })),
      h("label", { className: "field" }, h("span", null, "Priority"),
        h("select", { name: "priority", defaultValue: "MEDIUM" }, ["LOW", "MEDIUM", "HIGH", "URGENT"].map(value =>
          h("option", { key: value, value }, value)))),
      h("label", { className: "field" }, h("span", null, "Assign to"),
        h("select", { name: "assignedAgentId" }, h("option", { value: "" },
          actor.role === "SUPPORT_AGENT" ? "Assign to me" : "Unassigned"),
          agents.length === 0 &&
            h("option", { disabled: true }, "No agents — add one in Team"),
          agents.map(user =>
            h("option", { key: user.id, value: user.id }, user.name)))),
      h("div", { className: "form-actions" }, h("button", { type: "button", onClick: cancel }, "Cancel"),
        h("button", { className: "primary", type: "submit" }, "Create ticket")));
  }

  function teamManagement(users, createUser) {
    return h("div", { className: "team-layout" },
      h("form", { className: "create-card team-form", onSubmit: createUser },
        h("h2", null, "Add a team member"),
        h("p", { className: "muted" }, "Create an agent or customer account in this organization."),
        h("label", { className: "field" }, h("span", null, "Full name"),
          h("input", { name: "name", maxLength: 120, required: true })),
        h("label", { className: "field" }, h("span", null, "Email"),
          h("input", { name: "email", type: "email", maxLength: 190, required: true })),
        h("label", { className: "field" }, h("span", null, "Temporary password"),
          h("input", { name: "password", type: "password", minLength: 10, maxLength: 100, required: true })),
        h("label", { className: "field" }, h("span", null, "Role"),
          h("select", { name: "role", defaultValue: "SUPPORT_AGENT" },
            h("option", { value: "SUPPORT_AGENT" }, "Support Agent"),
            h("option", { value: "CUSTOMER" }, "Customer"))),
        h("button", { className: "primary", type: "submit" }, "Create user")),
      h("section", { className: "ticket-table team-table" },
        h("div", { className: "team-table-heading" }, h("span", null, "Organization users"), h("span", null, "Role")),
        users.length ? users.map(user => h("div", { className: "team-user-row", key: user.id },
          h("div", { className: "team-user-identity" },
            h("span", { className: "avatar" }, user.name.slice(0, 1).toUpperCase()),
            h("span", null, h("strong", null, user.name), h("small", null, user.email))),
          h("span", { className: `role-label role-${user.role.toLowerCase()}` }, roles[user.role]))) :
          h("div", { className: "empty-state" }, h("strong", null, "No organization users found"))));
  }

  function selectFilter(label, values, selected, onChange) {
    return h("select", { value: selected, onChange: event => onChange(event.target.value) },
      h("option", { value: "" }, label), values.map(value =>
        h("option", { key: value, value }, value.replaceAll("_", " "))));
  }

  function agentSelect(ticketId, selectedAgentId, supportUsers, assign) {
    const agents = supportUsers.filter(user => user.role === "SUPPORT_AGENT");
    return h("select", {
      className: "agent-select",
      value: selectedAgentId || "",
      "aria-label": "Assigned agent",
      onClick: event => event.stopPropagation(),
      onChange: event => assign(ticketId, event.target.value)
    },
    h("option", { value: "" }, "Unassigned"),
    agents.length === 0 && h("option", { disabled: true }, "No agents in organization"),
    agents.map(agent => h("option", { key: agent.id, value: agent.id }, agent.name)));
  }

  function badge(status) {
    return h("span", { className: `badge badge-${status.toLowerCase()}` }, status.replaceAll("_", " "));
  }

  function formatDate(value) {
    return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  }

  ReactDOM.createRoot(document.getElementById("root")).render(h(App));
})();
