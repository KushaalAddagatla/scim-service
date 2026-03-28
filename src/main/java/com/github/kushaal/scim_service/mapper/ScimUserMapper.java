package com.github.kushaal.scim_service.mapper;

import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.model.entity.ScimUserEmail;
import com.github.kushaal.scim_service.model.entity.ScimUserPhoneNumber;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.dto.response.ScimMeta;
import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
                .active(user.isActive())
                .emails(mapEmails(user.getEmails()))
                .phoneNumbers(mapPhones(user.getPhoneNumbers()))
                .meta(mapMeta(user))
                .schemas(List.of(ScimConstants.SCHEMA_USER))
                .build();
    }

    // ── DTO → Entity (create) ─────────────────────────────────────────────────

    public ScimUser toEntity(ScimUserDto dto) {
        ScimUser user = ScimUser.builder()
                .externalId(dto.getExternalId())
                .userName(dto.getUserName())
                .active(dto.getActive())
                .displayName(dto.getDisplayName())
                .build();

        if (dto.getName() != null) {
            user.setGivenName(dto.getName().getGivenName());
            user.setFamilyName(dto.getName().getFamilyName());
        }

        if (dto.getEmails() != null) {
            List<ScimUserEmail> emails = dto.getEmails().stream()
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

        if (dto.getPhoneNumbers() != null) {
            List<ScimUserPhoneNumber> phones = dto.getPhoneNumbers().stream()
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

    // ── DTO → Entity (update / PUT) ───────────────────────────────────────────

    public void updateEntity(ScimUser existing, ScimUserDto dto) {
        existing.setExternalId(dto.getExternalId());
        existing.setUserName(dto.getUserName());
        existing.setDisplayName(dto.getDisplayName());
        existing.setActive(dto.isActive());

        if (dto.getName() != null) {
            existing.setGivenName(dto.getName().getGivenName());
            existing.setFamilyName(dto.getName().getFamilyName());
        }

        // For multi-valued attributes on PUT: clear and replace
        // This is correct per RFC 7644 — PUT is a full replacement
        existing.getEmails().clear();
        if (dto.getEmails() != null) {
            dto.getEmails().forEach(e -> existing.getEmails().add(
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
        if (dto.getPhoneNumbers() != null) {
            dto.getPhoneNumbers().forEach(p -> existing.getPhoneNumbers().add(
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