package com.github.kushaal.scim_service.mapper;

import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.model.entity.ScimUserEmail;
import com.github.kushaal.scim_service.model.entity.ScimUserPhoneNumber;
import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.dto.response.ScimMeta;
import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScimUserMapper {

    // ── Entity → DTO ──────────────────────────────────────────────────────────

    public ScimUserDto toDto(ScimUser user) {
        return ScimUserDto.builder()
                .id(user.getId().toString())
                .externalId(user.getExternalId())
                .userName(user.getUserName())
                .name(mapName(user))
                .displayName(user.getDisplayName())
                .active(user.getActive())
                .emails(mapEmails(user.getEmails()))
                .phoneNumbers(mapPhones(user.getPhoneNumbers()))
                .meta(mapMeta(user))
                .schemas(List.of(ScimConstants.SCHEMA_USER))
                .build();
    }

    // ── Request → Entity (create) ─────────────────────────────────────────────

    public ScimUser toEntity(ScimUserRequest request) {
        ScimUser user = ScimUser.builder()
                .externalId(request.getExternalId())
                .userName(request.getUserName())
                .active(request.getActive())
                .displayName(request.getDisplayName())
                .build();

        if (request.getName() != null) {
            user.setGivenName(request.getName().getGivenName());
            user.setFamilyName(request.getName().getFamilyName());
            user.setMiddleName(request.getName().getMiddleName());
        }

        if (request.getEmails() != null) {
            List<ScimUserEmail> emails = request.getEmails().stream()
                    .map(e -> ScimUserEmail.builder()
                            .value(e.getValue())
                            .type(e.getType())
                            .primary(e.getPrimary())
                            .display(e.getDisplay())
                            .user(user)
                            .build())
                    .collect(Collectors.toList());
            user.setEmails(emails);
        }

        if (request.getPhoneNumbers() != null) {
            List<ScimUserPhoneNumber> phones = request.getPhoneNumbers().stream()
                    .map(p -> ScimUserPhoneNumber.builder()
                            .value(p.getValue())
                            .type(p.getType())
                            .primary(p.getPrimary())
                            .user(user)
                            .build())
                    .collect(Collectors.toList());
            user.setPhoneNumbers(phones);
        }

        return user;
    }

    // ── Request → Entity (update / PUT) ───────────────────────────────────────

    public void updateEntity(ScimUser existing, ScimUserRequest request) {
        existing.setExternalId(request.getExternalId());
        existing.setUserName(request.getUserName());
        existing.setDisplayName(request.getDisplayName());
        existing.setActive(request.getActive());

        if (request.getName() != null) {
            existing.setGivenName(request.getName().getGivenName());
            existing.setFamilyName(request.getName().getFamilyName());
            existing.setMiddleName(request.getName().getMiddleName());
        }

        // For multi-valued attributes on PUT: clear and replace
        // This is correct per RFC 7644 — PUT is a full replacement
        existing.getEmails().clear();
        if (request.getEmails() != null) {
            request.getEmails().forEach(e -> existing.getEmails().add(
                    ScimUserEmail.builder()
                            .value(e.getValue())
                            .type(e.getType())
                            .primary(e.getPrimary())
                            .display(e.getDisplay())
                            .user(existing)
                            .build()
            ));
        }

        existing.getPhoneNumbers().clear();
        if (request.getPhoneNumbers() != null) {
            request.getPhoneNumbers().forEach(p -> existing.getPhoneNumbers().add(
                    ScimUserPhoneNumber.builder()
                            .value(p.getValue())
                            .type(p.getType())
                            .primary(p.getPrimary())
                            .user(existing)
                            .build()
            ));
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ScimUserDto.NameDto mapName(ScimUser user) {
        if (user.getGivenName() == null && user.getFamilyName() == null) return null;
        return ScimUserDto.NameDto.builder()
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .build();
    }

    private List<ScimUserDto.EmailDto> mapEmails(List<ScimUserEmail> emails) {
        if (emails == null) return Collections.emptyList();
        return emails.stream()
                .map(e -> ScimUserDto.EmailDto.builder()
                        .value(e.getValue())
                        .type(e.getType())
                        .primary(e.getPrimary())
                        .display(e.getDisplay())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ScimUserDto.PhoneNumberDto> mapPhones(List<ScimUserPhoneNumber> phones) {
        if (phones == null) return Collections.emptyList();
        return phones.stream()
                .map(p -> ScimUserDto.PhoneNumberDto.builder()
                        .value(p.getValue())
                        .type(p.getType())
                        .primary(p.getPrimary())
                        .build())
                .collect(Collectors.toList());
    }

    private ScimMeta mapMeta(ScimUser user) {
        String location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/scim/v2/Users/{id}")
                .buildAndExpand(user.getId())
                .toUriString();

        return ScimMeta.builder()
                .resourceType("User")
                .created(user.getCreatedAt())
                .lastModified(user.getUpdatedAt())
                .location(location)
                .version("W/\"" + user.getMetaVersion() + "\"")
                .build();
    }
}