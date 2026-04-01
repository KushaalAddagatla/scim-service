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
public class ScimGroupDto {

    @Builder.Default
    private List<String> schemas = List.of(ScimConstants.SCHEMA_GROUP);

    private String id;
    private String externalId;
    private String displayName;
    private List<MemberDto> members;
    private ScimMeta meta;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemberDto {
        private String value;    // user UUID as string
        private String display;  // user's displayName at time of response

        // $ref is a reserved Java identifier — @JsonProperty maps the field name
        // to the correct SCIM attribute name in serialized output
        @JsonProperty("$ref")
        private String ref;      // absolute URI to the user resource
    }
}
