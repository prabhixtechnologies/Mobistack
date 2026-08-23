package com.fixflow.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_identities")
public class UserIdentity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 255)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
