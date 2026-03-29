package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.exception.ScimConflictException;
import com.github.kushaal.scim_service.exception.ScimResourceNotFoundException;
import com.github.kushaal.scim_service.mapper.ScimUserMapper;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScimUserServiceTest {

    @Mock ScimUserRepository userRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ScimUserMapper mapper;

    @InjectMocks ScimUserService userService;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_returnsDto_andWritesProvisionAuditLog() {
        ScimUserRequest request = buildRequest("john@example.com");
        ScimUser entity  = buildUser(null, "john@example.com", true);
        ScimUser saved   = buildUser(UUID.randomUUID(), "john@example.com", true);
        ScimUserDto dto  = ScimUserDto.builder().userName("john@example.com").build();

        when(userRepository.existsByUserName("john@example.com")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(dto);

        ScimUserDto result = userService.create(request);

        assertThat(result.getUserName()).isEqualTo("john@example.com");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEventType()).isEqualTo("PROVISION");
        assertThat(auditCaptor.getValue().getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void create_whenActiveIsNull_defaultsToTrue() {
        ScimUserRequest request = buildRequest("john@example.com");
        ScimUser entityWithNullActive = buildUser(null, "john@example.com", null);
        ScimUser saved = buildUser(UUID.randomUUID(), "john@example.com", true);

        when(userRepository.existsByUserName(any())).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entityWithNullActive);
        when(userRepository.save(entityWithNullActive)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(ScimUserDto.builder().build());

        userService.create(request);

        assertThat(entityWithNullActive.getActive()).isTrue();
    }

    @Test
    void create_whenUserNameAlreadyExists_throwsConflict() {
        when(userRepository.existsByUserName("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(buildRequest("john@example.com")))
                .isInstanceOf(ScimConflictException.class)
                .hasMessageContaining("john@example.com");

        verify(userRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_whenUserExists_returnsDto() {
        UUID id = UUID.randomUUID();
        ScimUser user = buildUser(id, "john@example.com", true);
        ScimUserDto dto = ScimUserDto.builder().id(id.toString()).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(mapper.toDto(user)).thenReturn(dto);

        assertThat(userService.findById(id).getId()).isEqualTo(id.toString());
    }

    @Test
    void findById_whenUserNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(ScimResourceNotFoundException.class);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsPaginatedListResponse() {
        ScimUser user = buildUser(UUID.randomUUID(), "john@example.com", true);
        ScimUserDto dto = ScimUserDto.builder().userName("john@example.com").build();
        var page = new PageImpl<>(List.of(user), PageRequest.of(0, 100), 1);

        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(mapper.toDto(user)).thenReturn(dto);

        ScimListResponse<ScimUserDto> result = userService.findAll(1, 100);

        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getStartIndex()).isEqualTo(1);
        assertThat(result.getItemsPerPage()).isEqualTo(1);
        assertThat(result.getResources()).hasSize(1);
    }

    @Test
    void findAll_convertsScimStartIndexToZeroBasedPage() {
        var page = new PageImpl<ScimUser>(List.of(), PageRequest.of(0, 10), 0);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        userService.findAll(3, 10);

        // startIndex=3 with count=10 → page index 2 (0-based), size 10
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_happyPath_incrementsMetaVersionAndReturnsDto() {
        UUID id = UUID.randomUUID();
        ScimUser existing = buildUser(id, "john@example.com", true);
        existing.setMetaVersion(1);
        ScimUserRequest request = buildRequest("john@example.com"); // same userName — no conflict check
        ScimUserDto dto = ScimUserDto.builder().build();

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(dto);

        userService.update(id, request);

        assertThat(existing.getMetaVersion()).isEqualTo(2);
        verify(mapper).updateEntity(existing, request);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEventType()).isEqualTo("SCIM_PUT");
    }

    @Test
    void update_whenChangingUserNameToExistingOne_throwsConflict() {
        UUID id = UUID.randomUUID();
        ScimUser existing = buildUser(id, "old@example.com", true);
        ScimUserRequest request = buildRequest("taken@example.com");

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUserName("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(ScimConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_whenUserNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(id, buildRequest("john@example.com")))
                .isInstanceOf(ScimResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_setsActiveToFalse_andWritesDeprovisionAuditLog() {
        UUID id = UUID.randomUUID();
        ScimUser user = buildUser(id, "john@example.com", true);
        user.setMetaVersion(1);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.delete(id);

        assertThat(user.getActive()).isFalse();
        assertThat(user.getMetaVersion()).isEqualTo(2);
        verify(userRepository).save(user);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEventType()).isEqualTo("DEPROVISION");
    }

    @Test
    void delete_whenUserNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(ScimResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScimUserRequest buildRequest(String userName) {
        return ScimUserRequest.builder().userName(userName).build();
    }

    private ScimUser buildUser(UUID id, String userName, Boolean active) {
        return ScimUser.builder()
                .id(id)
                .userName(userName)
                .active(active)
                .metaVersion(1)
                .build();
    }
}
