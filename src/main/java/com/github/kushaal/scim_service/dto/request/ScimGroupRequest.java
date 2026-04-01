package com.github.kushaal.scim_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimGroupRequest {

    private List<String> schemas;

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String externalId;

    // Each entry's value field is the user UUID to add as a member
    private List<MemberRequest> members;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberRequest {
        private String value;    // user UUID as string
        private String display;  // optional display name hint from the IdP
    }
}
