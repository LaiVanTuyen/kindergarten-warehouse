package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.CommentMapper;
import com.kindergarten.warehouse.repository.CommentRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.impl.CommentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService Unit Tests")
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommentMapper commentMapper;
    @Spy private com.kindergarten.warehouse.security.ResourceAccessGuard resourceAccessGuard =
            new com.kindergarten.warehouse.security.ResourceAccessGuard();

    @InjectMocks
    private CommentServiceImpl commentService;

    private User user;
    private Resource publicResource;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("teacher")
                .build();

        Category category = new Category();
        category.setIsDeleted(false);
        category.setVisibility(Visibility.PUBLIC);

        Topic topic = new Topic();
        topic.setIsDeleted(false);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setCategory(category);

        publicResource = new Resource();
        publicResource.setId("res-1");
        publicResource.setIsDeleted(false);
        publicResource.setStatus(ResourceStatus.APPROVED);
        publicResource.setVisibility(Visibility.PUBLIC);
        publicResource.setTopic(topic);
    }

    @Test
    @DisplayName("Nên throw INVALID_REQUEST nếu rating ngoài khoảng 1..5")
    void shouldRejectInvalidRating() {
        assertThatThrownBy(() -> commentService.createComment("res-1", "teacher", "Good", 6))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Nên throw INVALID_REQUEST nếu content rỗng")
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> commentService.createComment("res-1", "teacher", "   ", 5))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Nên không cho comment resource private")
    void shouldRejectPrivateResource() {
        publicResource.setVisibility(Visibility.PRIVATE);
        when(userRepository.findByUsername("teacher")).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(publicResource));

        assertThatThrownBy(() -> commentService.createComment("res-1", "teacher", "Useful", 5))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
