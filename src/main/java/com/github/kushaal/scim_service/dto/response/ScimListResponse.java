package com.github.kushaal.scim_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kushaal.scim_service.model.ScimConstants;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimListResponse<T> {

    @Builder.Default
    private List<String> schemas = List.of(ScimConstants.SCHEMA_LIST_RESPONSE);

    private int totalResults;
    private int startIndex;
    private int itemsPerPage;

    // SCIM requires capital R "Resources" — JsonProperty maps it
    @JsonProperty("Resources")
    private List<T> resources;
}