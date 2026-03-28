package com.github.kushaal.scim_service.model;

public final class ScimConstants {

    private ScimConstants() {}

    // Schema URNs
    public static final String SCHEMA_USER =
            "urn:ietf:params:scim:schemas:core:2.0:User";

    public static final String SCHEMA_GROUP =
            "urn:ietf:params:scim:schemas:core:2.0:Group";

    public static final String SCHEMA_LIST_RESPONSE =
            "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    public static final String SCHEMA_ERROR =
            "urn:ietf:params:scim:api:messages:2.0:Error";

    public static final String SCHEMA_PATCH_OP =
            "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    public static final String SCHEMA_ENTERPRISE_USER =
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    // Resource types
    public static final String RESOURCE_TYPE_USER  = "User";
    public static final String RESOURCE_TYPE_GROUP = "Group";

    // Content type — Okta rejects application/json
    public static final String SCIM_CONTENT_TYPE = "application/scim+json";
}