package com.github.kushaal.scim_service.model.entity;

import com.github.kushaal.scim_service.model.entity.ScimUser;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "scim_user_phone_numbers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimUserPhoneNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ScimUser user;

    @Column(nullable = false)
    private String value;

    private String type;      // work, home, mobile, fax, other

    @Column(name = "primary_phone")
    private Boolean primary = false;
}