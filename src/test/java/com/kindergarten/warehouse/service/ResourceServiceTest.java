package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.*;
import com.kindergarten.warehouse.event.ResourceRejectedEvent;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.FavoriteRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ResourceServiceImpl resourceService;

    private User adminUser;
    private User normalUser;
    private Resource testResource;
    private ResourceResponse mockResponse;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(java.util.Set.of(Role.ADMIN));

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUsername("user1");
        normalUser.setRoles(java.util.Set.of(Role.USER));

        Category mockCategory = new Category();
        mockCategory.setIsDeleted(false);
        mockCategory.setIsActive(true);

        Topic mockTopic = new Topic();
        mockTopic.setIsDeleted(false);
        mockTopic.setIsActive(true);
        mockTopic.setCategory(mockCategory);

        testResource = new Resource();
        testResource.setId("res-1");
        testResource.setTitle("Test Resource");
        testResource.setSlug("test-resource");
        testResource.setCreatedBy(normalUser.getId());
        testResource.setTopic(mockTopic);
        testResource.setVisibility(Visibility.PUBLIC);
        testResource.setStatus(ResourceStatus.APPROVED);
        testResource.setIsDeleted(false);

        mockResponse = new ResourceResponse();
        mockResponse.setId("res-1");
        mockResponse.setTitle("Test Resource");
    }

    @Nested
    @DisplayName("Tests for getResourceBySlug")
    class GetResourceBySlugTests {

        @Test
        @DisplayName("Should throw RESOURCE_NOT_FOUND when slug does not exist")
        void shouldThrowExceptionWhenSlugNotFound() {
            when(resourceRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> 
                resourceService.getResourceBySlug("unknown-slug"));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
            verify(resourceMapper, never()).toResponse(any(), anyBoolean());
        }

        @Test
        @DisplayName("Should return ResourceResponse when slug is valid")
        void shouldReturnResponseWhenSlugIsValid() {
            when(resourceRepository.findBySlug("test-resource")).thenReturn(Optional.of(testResource));
            when(resourceMapper.toResponse(testResource, false)).thenReturn(mockResponse);

            ResourceResponse response = resourceService.getResourceBySlug("test-resource");

            assertNotNull(response);
            assertEquals("res-1", response.getId());
        }
    }

    @Nested
    @DisplayName("Tests for approveResource")
    class ApproveResourceTests {

        @Test
        @DisplayName("Should approve resource successfully if user is ADMIN")
        void shouldApproveResourceWhenAdmin() {
            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(resourceRepository.save(any(Resource.class))).thenReturn(testResource);
            when(resourceMapper.toResponse(testResource, false)).thenReturn(mockResponse);

            ResourceResponse response = resourceService.approveResource("res-1", "admin");

            assertNotNull(response);
            assertEquals(ResourceStatus.APPROVED, testResource.getStatus());
            assertNull(testResource.getRejectionReason());
            verify(auditLogService, times(1)).saveLog(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw RESOURCE_FORBIDDEN if user is not ADMIN")
        void shouldThrowForbiddenIfNotAdmin() {
            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("user1")).thenReturn(Optional.of(normalUser));

            AppException exception = assertThrows(AppException.class, () -> 
                resourceService.approveResource("res-1", "user1"));

            assertEquals(ErrorCode.RESOURCE_FORBIDDEN, exception.getErrorCode());
            verify(resourceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Tests for rejectResource")
    class RejectResourceTests {

        @Test
        @DisplayName("Should reject resource successfully and publish event if user is ADMIN")
        void shouldRejectResourceWhenAdmin() {
            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(resourceRepository.save(any(Resource.class))).thenReturn(testResource);
            when(resourceMapper.toResponse(testResource, false)).thenReturn(mockResponse);
            // Mocking finding original creator
            when(userRepository.findById(testResource.getCreatedBy())).thenReturn(Optional.of(normalUser));

            ResourceResponse response = resourceService.rejectResource("res-1", "Violates policy", "admin");

            assertNotNull(response);
            assertEquals(ResourceStatus.REJECTED, testResource.getStatus());
            assertEquals("Violates policy", testResource.getRejectionReason());
            verify(auditLogService, times(1)).saveLog(any(), any(), any(), any(), any(), any());
            verify(eventPublisher, times(1)).publishEvent(any(ResourceRejectedEvent.class));
        }

        @Test
        @DisplayName("Should throw INVALID_REQUEST if rejection reason is empty")
        void shouldThrowExceptionIfReasonEmpty() {
            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            AppException exception = assertThrows(AppException.class, () -> 
                resourceService.rejectResource("res-1", "", "admin"));

            assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verify(resourceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Tests for updateVisibility")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("Should throw RESOURCE_FORBIDDEN if user is neither admin nor owner")
        void shouldThrowForbiddenIfNotOwnerOrAdmin() {
            User otherUser = new User();
            otherUser.setId(3L);
            otherUser.setUsername("user2");
            otherUser.setRoles(java.util.Set.of(Role.USER));

            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("user2")).thenReturn(Optional.of(otherUser));

            VisibilityUpdateRequest request = new VisibilityUpdateRequest();
            request.setVisibility(Visibility.PRIVATE);

            AppException exception = assertThrows(AppException.class, () -> 
                resourceService.updateVisibility("res-1", request, "user2"));

            assertEquals(ErrorCode.RESOURCE_FORBIDDEN, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should update visibility successfully if user is owner")
        void shouldUpdateVisibilityWhenOwner() {
            when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(testResource));
            when(userRepository.findByUsername("user1")).thenReturn(Optional.of(normalUser));
            when(resourceRepository.save(any(Resource.class))).thenReturn(testResource);
            when(resourceMapper.toResponse(testResource, false)).thenReturn(mockResponse);

            VisibilityUpdateRequest request = new VisibilityUpdateRequest();
            request.setVisibility(Visibility.PRIVATE);

            ResourceResponse response = resourceService.updateVisibility("res-1", request, "user1");

            assertNotNull(response);
            assertEquals(Visibility.PRIVATE, testResource.getVisibility());
            verify(resourceRepository, times(1)).save(testResource);
        }
    }
}
