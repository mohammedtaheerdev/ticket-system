package com.ticket.system.repository;

import com.ticket.system.model.TicketHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
