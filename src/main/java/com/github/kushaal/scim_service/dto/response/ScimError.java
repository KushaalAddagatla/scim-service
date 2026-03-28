package com.github.kushaal.scim_service.dto.response;

import com.github.kushaal.scim_service.model.ScimConstants;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimError {

    @Builder.Default
    private List<String> schemas = List.of(ScimConstants.SCHEMA_ERROR);

    private String scimType;   // invalidValue, uniqueness, mutability, etc.
    private String detail;     // human-readable message
    private int status;        // HTTP status code repeated in body per SCIM spec
}