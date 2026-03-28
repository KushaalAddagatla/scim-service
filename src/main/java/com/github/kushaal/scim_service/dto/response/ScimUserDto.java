package com.github.kushaal.scim_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.github.kushaal.scim_service.model.ScimConstants;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUserDto {

    // Always present — tells the client what type this is
    @Builder.Default
    private List<String> schemas = List.of(ScimConstants.SCHEMA_USER);

    private String id;
    private String externalId;
    private String userName;
    private Boolean active;
    private String displayName;

    // SCIM nested object: "name": { "givenName": "...", "familyName": "..." }
    private NameDto name;

    private List<EmailDto> emails;
    private List<PhoneNumberDto> phoneNumbers;

    private String title;
    private String locale;
    private String timezone;
    private String profileUrl;

    private ScimMeta meta;

    // --- Nested DTOs ---

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NameDto {
        private String givenName;
        private String familyName;
        private String middleName;
        private String formatted;   // "John Michael Doe"
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmailDto {
        private String value;
        private String type;
        @JsonProperty("primary")
        private Boolean primary;
        private String display;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhoneNumberDto {
        private String value;
        private String type;
        @JsonProperty("primary")
        private Boolean primary;
    }
}