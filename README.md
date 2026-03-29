# SCIM-Compliant Identity Provisioning Service with Automated Access Lifecycle

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.0.5](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
![SCIM 2.0](https://img.shields.io/badge/SCIM-2.0-orange)
![CI](https://github.com/KushaalAddagatla/scim-service/actions/workflows/ci.yml/badge.svg)

| SCIM 2.0 (RFC 7643/7644) | Spring Boot | OAuth 2.0 / JWT | Access Certification | AWS ECS + SES | React |
|:---:|:---:|:---:|:---:|:---:|:---:|

---

## Overview

A fully SCIM 2.0 compliant user provisioning and deprovisioning service — the category of system enterprises pay Okta and SailPoint hundreds of thousands of dollars for. It acts as a SCIM server that any standard identity provider (Okta, Azure AD, or any SCIM 2.0 client) can connect to, automatically provisioning and deprovisioning users and groups across downstream applications.

Beyond provisioning, it implements an **access certification campaign engine** — the IGA (Identity Governance and Administration) capability that satisfies SOC2 CC6.3, HIPAA Minimum Necessary, and ISO 27001 periodic access review requirements. Stale access is flagged automatically, routed to managers for approve/revoke decisions, and fully audit-logged.

*You can test the full end-to-end flow using a free Okta developer tenant as the SCIM client calling your Spring Boot SCIM server. Real IdP, real provisioning, real demo.*

---

## Architecture

![Architecture Diagram](docs/architecture.png)

---

## Tech Stack

| Component | Role & Detail |
|---|---|
| **Spring Boot** | SCIM 2.0 REST API implementation — all standard endpoints per RFC 7643/7644. OAuth 2.0 + JWT token introspection securing all SCIM endpoints. |
| **PostgreSQL** | Identity store: users, groups, memberships, provisioning events, access history, certification records, full audit log. |
| **AWS ECS** | Containerized deployment. Task definition stores service credentials via Secrets Manager — dogfooding the security pattern. |
| **AWS Secrets Manager** | Downstream application credentials. Referenced by ECS task definition, not hardcoded anywhere. |
| **AWS SES** | Certification campaign email delivery to managers. Tokenized approve/revoke links with 7-day expiry. |
| **AWS CloudWatch** | Provisioning event log stream. Alerting on failed provisioning, expired certification tasks. |
| **React Dashboard** | User directory, group memberships, active certification campaigns, audit log viewer, provisioning event timeline. |
| **Docker + Terraform** | Infrastructure as code. Terraform manages ECS cluster, SES config, CloudWatch log groups. Shows deployment maturity. |
| **GitHub Actions** | CI/CD: build → test → Docker image → push ECR → update ECS task definition. |
| **Okta Dev Tenant (free)** | SCIM 2.0 client for testing. Proves your server is spec-compliant, not just internally tested. |

---

## SCIM 2.0 Endpoints Implemented (RFC 7643 / RFC 7644)

Implementing the full SCIM spec — not just the happy-path endpoints — is what separates this from a basic REST API. Enterprise IdPs exercise all of these.

```
# User endpoints
GET    /scim/v2/Users              # list users (pagination + filtering: ?filter=userName eq "john")
POST   /scim/v2/Users              # provision new user
GET    /scim/v2/Users/{id}         # get specific user
PUT    /scim/v2/Users/{id}         # full replace update
PATCH  /scim/v2/Users/{id}         # partial update (most common in real provisioning flows)
DELETE /scim/v2/Users/{id}         # deprovision user

# Group endpoints
GET    /scim/v2/Groups             # list groups
POST   /scim/v2/Groups             # create group
GET    /scim/v2/Groups/{id}        # get group
PATCH  /scim/v2/Groups/{id}        # add/remove members (most common group operation)
DELETE /scim/v2/Groups/{id}        # delete group

# Discovery endpoints (required for IdP compatibility)
GET    /scim/v2/ServiceProviderConfig   # capability advertisement
GET    /scim/v2/Schemas                 # schema definitions
GET    /scim/v2/ResourceTypes           # resource type metadata

# Standout differentiator
POST   /scim/v2/Bulk               # bulk operations (rarely implemented, enterprises need it
                                   # for acquisitions: 500 users provisioned in one request)
```

**PATCH operations** are the hardest to implement correctly. They use JSON Patch (RFC 6902) semantics — `add`, `remove`, `replace` operations on specific attribute paths including multi-valued filters like `emails[type eq "work"].value`. Most tutorials only implement PUT. Getting PATCH right demonstrates you read the spec, not just a blog post.

---

## What's Built

### Completed — User CRUD + PATCH + Filtering + ETag

- **All 6 SCIM User endpoints** — `POST` (provision, 201 + Location header), `GET` by ID, `GET` list with pagination, `PUT` (full replace), `PATCH` (RFC 6902 JSON Patch), `DELETE` (soft delete, audit-safe)
- **SCIM Discovery endpoints** — `ServiceProviderConfig`, `Schemas`, `ResourceTypes` — required for Okta/Azure AD compatibility
- **JSON Patch (RFC 6902 + RFC 7644)** — the hardest SCIM endpoint, fully implemented:
  - Path-based ops: `{ "op": "replace", "path": "active", "value": false }`
  - Path-less ops: `{ "op": "replace", "value": { "active": false, "name": { "givenName": "John" } } }`
  - Multi-valued attribute paths with filters: `emails[type eq "work"].value` — find the email entry where type=work, update its value
  - All three operations: `add`, `remove`, `replace` on scalar and multi-valued attributes
- **ETag / Optimistic Concurrency** — `meta.version` as weak ETag (`W/"1"`), `If-Match` header on PUT/PATCH rejects stale writes with 412 Precondition Failed
- **SCIM Filtering** — `?filter=userName eq "john"`, `?filter=emails.value eq "john@example.com"`, supports `userName`, `externalId`, `active`, `emails.value` via JPA Specifications
- **Pagination** — 1-based `startIndex` per SCIM spec (not 0-based), `count` parameter, response includes `totalResults`, `startIndex`, `itemsPerPage`, `Resources`
- **SCIM-compliant error responses** — all errors return `application/scim+json` with `ScimError` DTO (never Spring's default). 400 for malformed PATCH, 404 for not found, 409 for uniqueness conflicts, 412 for ETag mismatch
- **Audit logging** — every SCIM operation writes to `audit_log` with event type, actor, target user, raw SCIM operation (JSONB), outcome, source IP, and correlation ID
- **Correlation ID tracing** — `CorrelationIdFilter` generates/reads `X-Correlation-ID`, propagates via MDC to all audit log entries and structured logs
- **Flyway-managed schema** — `ddl-auto: validate`, all schema changes via numbered migrations
- **Seed data** — 4 demo users with emails, phone numbers, and audit log entries pre-populated
- **Integration tests** — Testcontainers with real PostgreSQL, covering all endpoints, error cases, ETag flows, PATCH operations, filter queries, and pagination edge cases
- **Unit tests** — service layer with mocked repositories
- **CI pipeline** — GitHub Actions: build + test on push to main

### Coming Next — Groups, JWT Auth, Okta Integration

- **Group endpoints** — CRUD + PATCH for group membership (`add`/`remove` members)
- **OAuth 2.0 + JWT** — Bearer token validation on all SCIM endpoints, signing key in Secrets Manager
- **Okta developer tenant integration** — real IdP provisioning a real user end-to-end
- **Rate limiting** — Bucket4j, 100 req/min per source IP

### Planned — Access Certification Engine + Dashboard

- **Certification scheduler** — weekly job detects stale access (>90 days inactive), creates certification campaigns
- **Tokenized email links** — JWT-signed approve/revoke links via SES, single-use enforcement, 7-day expiry
- **Fail-secure escalation** — auto-suspend on manager non-response (satisfies SOC2 CC6.3)
- **React dashboard** — user directory, group view, certification campaigns, audit log with CSV export
- **Bulk endpoint** — RFC 7644 Section 3.7 for enterprise M&A onboarding at scale
- **Terraform + ECS deployment** — infrastructure as code, Secrets Manager integration

---

## Access Certification Engine

The certification engine is what elevates this from "SCIM server" to "IGA platform." This is the capability that satisfies SOC2 CC6.3 (periodic access reviews) and HIPAA Minimum Necessary access controls.

```python
# Weekly scheduled job
CertificationScheduler.run():

    # Step 1: Detect stale access
    SELECT user_id, resource_id, last_accessed_at
    FROM access_history
    WHERE last_accessed_at < NOW() - INTERVAL '90 days'
    AND access_status = 'ACTIVE'

    # Step 2: Create certification campaign
    INSERT INTO certifications (id, user_id, resource_id, reviewer_id,
                                expires_at, status)
    VALUES (uuid, user_id, resource_id, manager_id,
            NOW() + INTERVAL '7 days', 'PENDING')

    # Step 3: Send tokenized email to manager via AWS SES
    token = JWT.sign({ cert_id, action: "review" }, secret, expiresIn: "7d")
    SES.send(manager_email, approveLink=token, revokeLink=token)

    # Step 4a: Manager approves → access retained, audit log entry written
    # Step 4b: Manager revokes → SCIM PATCH fires to downstream app
    PATCH /scim/v2/Users/{id}
    { "op": "replace", "path": "active", "value": false }

    # Step 5: Audit log entry in PostgreSQL + CloudWatch event published
    INSERT INTO audit_log (timestamp, actor, action, resource, outcome, ip)
```

*Escalation path: If a manager does not respond within 7 days, access is automatically suspended (fail-secure default). A second email notifies the manager and their manager. This mirrors how enterprise IGA tools handle non-response.*

---

## Audit Trail & Compliance Logging

Every provisioning event, SCIM operation, certification decision, and access change is written to an immutable audit log in PostgreSQL and streamed to CloudWatch. The audit schema captures:

```sql
audit_log table:
    timestamp          -- event time (UTC)
    event_type         -- PROVISION | DEPROVISION | CERTIFY_APPROVE | CERTIFY_REVOKE | SCIM_PATCH
    actor              -- system (SCIM IdP) or human (manager email)
    target_user_id     -- affected user
    resource_id        -- application or group affected
    scim_operation     -- raw SCIM request body (JSONB)
    outcome            -- SUCCESS | FAILED | PENDING
    source_ip          -- originating IP
    correlation_id     -- trace ID across distributed operations
```

The React dashboard surfaces this as a searchable, filterable audit log with export to CSV. In a real SOC2 audit this is one of the first things an auditor asks for.

---

## Security Design

| Control | Implementation |
|---|---|
| **OAuth 2.0 + JWT** | All SCIM endpoints require a valid Bearer token. Token introspection validates claims (scope, expiry, issuer) on every request. Secrets Manager holds the signing key. |
| **Tokenized cert links** | Approve/revoke links in emails are short-lived JWTs (7-day expiry). Single-use — link is invalidated after first click. Prevents replay attacks. |
| **Secrets Manager** | Downstream app credentials stored in Secrets Manager. ECS task definition references secrets by ARN, never by value. Terraform manages the rotation schedule. |
| **HTTPS everywhere** | TLS termination at the load balancer (ACM certificate). Internal service communication uses private VPC subnets. |
| **Rate limiting** | SCIM endpoints rate-limited per source IP to prevent enumeration attacks. Spring Boot filter layer. |
| **Input validation** | SCIM schema validation on every inbound request. Malformed PATCH operations rejected with RFC-compliant 400 responses, not 500s. |

---

## Quick Start

### Prerequisites

- **Java 21**
- **Docker** (for PostgreSQL via docker-compose, and Testcontainers in tests)

### Run locally (under 5 minutes)

```bash
# 1. Clone the repo
git clone https://github.com/KushaalAddagatla/scim-service.git
cd scim-service

# 2. Start PostgreSQL + LocalStack
docker-compose up -d

# 3. Build and run tests
./mvnw clean install

# 4. Start the application
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` with seed data (4 users) pre-loaded via Flyway.

### Try it

```bash
# List all users
curl -s http://localhost:8080/scim/v2/Users | jq

# Get a specific user
curl -s http://localhost:8080/scim/v2/Users/{id} | jq

# Provision a new user
curl -s -X POST http://localhost:8080/scim/v2/Users \
  -H "Content-Type: application/scim+json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "jane.doe@example.com",
    "name": { "givenName": "Jane", "familyName": "Doe" },
    "emails": [{ "value": "jane.doe@example.com", "type": "work", "primary": true }],
    "active": true
  }' | jq

# Filter by userName
curl -s 'http://localhost:8080/scim/v2/Users?filter=userName%20eq%20%22alice%22' | jq

# PATCH a user (the hard one — RFC 6902)
curl -s -X PATCH http://localhost:8080/scim/v2/Users/{id} \
  -H "Content-Type: application/scim+json" \
  -H 'If-Match: W/"1"' \
  -d '{
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
    "Operations": [
      { "op": "replace", "path": "active", "value": false },
      { "op": "replace", "path": "emails[type eq \"work\"].value", "value": "new@example.com" }
    ]
  }' | jq
```

### Run tests

```bash
# All tests (requires Docker for Testcontainers)
./mvnw test

# Single test class
./mvnw test -Dtest=ScimUserControllerIT
./mvnw test -Dtest=ScimUserPatchIT
```

---

## Project Structure

```
src/main/java/com/github/kushaal/scim_service/
├── controller/
│   ├── ScimUserController.java          # All 6 SCIM User endpoints
│   └── ScimDiscoveryController.java     # ServiceProviderConfig, Schemas, ResourceTypes
├── service/
│   ├── ScimUserService.java             # Business logic, audit logging, ETag validation
│   └── PatchApplier.java                # RFC 6902 JSON Patch engine
├── repository/
│   ├── ScimUserRepository.java          # JPA + Specification queries
│   └── AuditLogRepository.java          # Audit trail persistence
├── model/entity/
│   ├── ScimUser.java                    # Core user entity, UUID PK
│   ├── ScimUserEmail.java               # Multi-valued emails (@OneToMany)
│   ├── ScimUserPhoneNumber.java         # Multi-valued phone numbers (@OneToMany)
│   └── AuditLog.java                    # Immutable audit log with JSONB operations
├── dto/
│   ├── request/                         # ScimUserRequest, ScimPatchRequest, ScimPatchOperation
│   └── response/                        # ScimUserDto, ScimListResponse, ScimMeta, ScimError
├── mapper/
│   └── ScimUserMapper.java              # Entity ↔ DTO mapping
├── filter/
│   ├── ScimFilterParser.java            # SCIM filter expression parser (eq operator)
│   └── ScimUserSpecification.java       # JPA Specification for dynamic WHERE clauses
├── config/
│   ├── SecurityConfig.java              # Spring Security (permitAll → JWT in Week 3)
│   └── CorrelationIdFilter.java         # X-Correlation-ID generation + MDC propagation
└── exception/
    ├── ScimExceptionHandler.java        # Global handler → ScimError responses
    ├── ScimResourceNotFoundException.java
    ├── ScimConflictException.java
    ├── ScimInvalidValueException.java
    └── ScimPreconditionFailedException.java

src/main/resources/db/migration/
├── V1__create_users_table.sql           # Schema: users, emails, phones, audit_log + indexes
└── V2__seed_data.sql                    # 4 demo users with realistic data
```

---

## Build Roadmap

| Step | Deliverable | Status |
|---|---|---|
| **Step 1** | SCIM User endpoints: GET list, POST provision, GET by ID, PUT, DELETE. PostgreSQL identity store with Flyway migrations. Seed data. | **Done** |
| **Step 2** | SCIM PATCH for Users — JSON Patch (RFC 6902) with multi-valued attribute paths, ETag/If-Match concurrency, SCIM filtering, pagination. Integration + unit tests. | **Done** |
| **Step 3** | SCIM Group endpoints + PATCH for group membership. OAuth 2.0 / JWT protection on all endpoints. | Next |
| **Step 4** | Connect Okta developer tenant as SCIM client. Provision a real test user end-to-end. Record a demo video. | Planned |
| **Step 5** | Access certification engine: stale access detection, PostgreSQL certification table, SES email with tokenized links. | Planned |
| **Step 6** | React dashboard: user directory, group view, certification campaigns, audit log. Terraform + ECS deployment. | Planned |
| **Step 7** | Bulk endpoint, CloudWatch alerting, auto-suspend escalation path. | Planned |

