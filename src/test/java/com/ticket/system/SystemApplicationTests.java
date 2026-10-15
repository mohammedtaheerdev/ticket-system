package com.ticket.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SystemApplicationTests {
	@Autowired private OrganizationService organizations;
	@Autowired private AuthenticationManager authenticationManager;
	@Autowired private AuthenticationEntryPoint authenticationEntryPoint;

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

}
