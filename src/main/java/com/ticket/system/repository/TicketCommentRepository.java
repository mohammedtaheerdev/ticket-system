package com.ticket.system.repository;

import com.ticket.system.model.TicketComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
