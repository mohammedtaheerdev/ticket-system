package com.ticket.system;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticket.system.api.dto.ApiDtos;
import com.ticket.system.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SystemApplicationTests {
	@Autowired private OrganizationService organizations;
	@Autowired private AuthenticationManager authenticationManager;

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

}
