package com.ticket.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.security.LoginRateLimiter;
import com.ticket.system.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SystemApplicationTests {
	@Autowired private OrganizationService organizations;
	@Autowired private AuthenticationManager authenticationManager;
	@Autowired private AuthenticationEntryPoint authenticationEntryPoint;
	@Autowired private MockMvc mockMvc;
	@Autowired private LoginRateLimiter loginRateLimiter;
	@Value("${server.servlet.session.timeout}") private String sessionTimeout;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void registeredAdminCanAuthenticateWithStoredPassword() {
		organizations.register(new ApiDtos.OrganizationRegistration(
				"Test Workspace", "Test Admin", "admin@test.example", "secure-password-123"));

		var authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken("admin@test.example", "secure-password-123"));

		assertTrue(authentication.isAuthenticated());
	}

	@Test
	void unauthorizedRequestsDoNotTriggerBrowserBasicAuthPrompt() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		authenticationEntryPoint.commence(new MockHttpServletRequest(), response,
				new BadCredentialsException("Invalid credentials"));

		assertEquals(401, response.getStatus());
		assertNull(response.getHeader("WWW-Authenticate"));
		assertTrue(response.getContentAsString().contains("Authentication is required"));
	}

	@Test
	void loginUsesExpiringSessionAndLogoutInvalidatesIt() throws Exception {
		assertEquals("30m", sessionTimeout);
		organizations.register(new ApiDtos.OrganizationRegistration(
				"Session Workspace", "Session Admin", "session-admin@test.example", "secure-password-123"));

		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		String csrfToken = jsonString(csrfResult.getResponse().getContentAsString(), "token");
		assertNotNull(csrfCookie);

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.cookie(csrfCookie)
						.header("X-XSRF-TOKEN", csrfToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"session-admin@test.example","password":"secure-password-123"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		assertNotNull(session);
		assertNotNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk());

		MvcResult refreshedCsrf = mockMvc.perform(get("/api/auth/csrf").session(session))
				.andExpect(status().isOk())
				.andReturn();
		Cookie logoutCsrfCookie = refreshedCsrf.getResponse().getCookie("XSRF-TOKEN");
		assertNotNull(logoutCsrfCookie);
		String logoutCsrfToken = jsonString(refreshedCsrf.getResponse().getContentAsString(), "token");
		mockMvc.perform(post("/api/auth/logout")
						.session(session)
						.cookie(logoutCsrfCookie)
						.header("X-XSRF-TOKEN", logoutCsrfToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void organizationRegistrationCreatesSessionWithoutGoingThroughRateLimitedLogin() throws Exception {
		String clientAddress = "192.0.2.55";
		for (int attempt = 0; attempt < 5; attempt++) {
			loginRateLimiter.recordFailure(clientAddress);
		}
		RequestPostProcessor fromClient = request -> {
			request.setRemoteAddr(clientAddress);
			return request;
		};

		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").with(fromClient))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		String csrfToken = jsonString(csrfResult.getResponse().getContentAsString(), "token");
		assertNotNull(csrfCookie);

		MvcResult registration = mockMvc.perform(post("/api/auth/register")
						.with(fromClient)
						.cookie(csrfCookie)
						.header("X-XSRF-TOKEN", csrfToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"organizationName":"Rate Limited Workspace","adminName":"Admin",
								 "email":"rate-limited-admin@test.example","password":"secure-password-123"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
		assertNotNull(session);
		assertNotNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
		mockMvc.perform(get("/api/auth/me").session(session).with(fromClient))
				.andExpect(status().isOk());
		loginRateLimiter.recordSuccess(clientAddress);
	}

	@Test
	void organizationRegistrationEndpointRejectsSixthAttemptFromSameClient() throws Exception {
		String clientAddress = "192.0.2.56";
		RequestPostProcessor fromClient = request -> {
			request.setRemoteAddr(clientAddress);
			return request;
		};

		for (int attempt = 1; attempt <= 5; attempt++) {
			MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").with(fromClient))
					.andExpect(status().isOk())
					.andReturn();
			Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
			assertNotNull(csrfCookie);
			String csrfToken = jsonString(csrfResult.getResponse().getContentAsString(), "token");

			mockMvc.perform(post("/api/auth/register")
							.with(fromClient)
							.cookie(csrfCookie)
							.header("X-XSRF-TOKEN", csrfToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"organizationName":"Limited Workspace %d","adminName":"Admin",
									 "email":"limited-admin-%d@test.example","password":"secure-password-123"}
									""".formatted(attempt, attempt)))
					.andExpect(status().isCreated());
		}

		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").with(fromClient))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		assertNotNull(csrfCookie);
		String csrfToken = jsonString(csrfResult.getResponse().getContentAsString(), "token");
		mockMvc.perform(post("/api/auth/register")
						.with(fromClient)
						.cookie(csrfCookie)
						.header("X-XSRF-TOKEN", csrfToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"organizationName":"Limited Workspace 6","adminName":"Admin",
								 "email":"limited-admin-6@test.example","password":"secure-password-123"}
								"""))
				.andExpect(status().isTooManyRequests());
	}

	private String jsonString(String json, String field) {
		return com.jayway.jsonpath.JsonPath.read(json, "$." + field);
	}

}
