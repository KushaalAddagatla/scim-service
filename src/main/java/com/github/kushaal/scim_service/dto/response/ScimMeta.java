package com.github.kushaal.scim_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimMeta {
    private String resourceType;
    private ZonedDateTime created;
    private ZonedDateTime lastModified;
    private String location;    // URL to this resource: /scim/v2/Users/{id}
    private String version;     // ETag: "W/\"1\""
}