package com.finance.portal.support.repository;

import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.domain.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<SupportTicket> findByIdAndUserId(UUID id, String userId);

    /** Kullanıcının aktif (çözülmemiş) talep sayısı — "aynı anda max 3" sınırı için. */
    long countByUserIdAndStatusNot(String userId, SupportTicketStatus status);

    /** Aktif (çözülmemiş) talebi olan kullanıcı id'leri — admin listesi "talep açanlar" filtresi için. */
    @Query("select distinct t.userId from SupportTicket t "
            + "where t.status <> com.finance.portal.support.domain.SupportTicketStatus.RESOLVED")
    List<String> findUserIdsWithActiveTickets();

    /** [userId, aktif talep sayısı] — admin listesinde satır rozeti için. */
    @Query("select t.userId, count(t) from SupportTicket t "
            + "where t.status <> com.finance.portal.support.domain.SupportTicketStatus.RESOLVED group by t.userId")
    List<Object[]> countActiveGroupedByUser();
}
