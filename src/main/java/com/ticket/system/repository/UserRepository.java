package com.ticket.system.repository;

import com.ticket.system.model.AppUser;
import com.ticket.system.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    @EntityGraph(attributePaths = "organization")
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByIdAndOrganizationId(Long id, Long organizationId);
    List<AppUser> findAllByOrganizationIdOrderByNameAsc(Long organizationId);
    List<AppUser> findAllByOrganizationIdAndRoleInOrderByNameAsc(Long organizationId, List<Role> roles);
}
