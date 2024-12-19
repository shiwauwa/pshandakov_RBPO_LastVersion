package org.example.pshandakov.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime serverDate;


    private int ticketLifetime;


    private Date activationDate;


    private Date expirationDate;


    private Long userId;


    private Long deviceId;


    private boolean isBlocked;

    private String digitalSignature;


    public static Ticket createTicket(Long userId, boolean isBlocked, Date expirationDate) {
        Ticket ticket = new Ticket();
        ticket.setServerDate(LocalDateTime.now());
        ticket.setTicketLifetime(2);
        ticket.setActivationDate(new Date());
        ticket.setExpirationDate(expirationDate);
        ticket.setUserId(userId);
        ticket.setBlocked(isBlocked);
        ticket.setDigitalSignature(UUID.randomUUID().toString());

        return ticket;
    }


}
