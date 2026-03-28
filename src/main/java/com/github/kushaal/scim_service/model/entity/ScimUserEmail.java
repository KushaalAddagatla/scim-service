package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "scim_user_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimUserEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ScimUser user;

    @Column(nullable = false)
    private String value;

    private String type;      // work, home, other

    @Column(name = "primary_email")
    private Boolean primary = false;

    private String display;
}