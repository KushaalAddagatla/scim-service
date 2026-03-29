package com.github.kushaal.scim_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimUserRequest {

    private List<String> schemas;

    @NotBlank(message = "userName is required")
    private String userName;

    private String externalId;
    private String displayName;
    private Boolean active;   // null → service defaults to true on create

    private NameRequest name;
    private List<EmailRequest> emails;
    private List<PhoneNumberRequest> phoneNumbers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NameRequest {
        private String givenName;
        private String familyName;
        private String middleName;
        private String formatted;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmailRequest {
        private String value;
        private String type;
        private Boolean primary;
        private String display;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PhoneNumberRequest {
        private String value;
        private String type;
        private Boolean primary;
    }
}
