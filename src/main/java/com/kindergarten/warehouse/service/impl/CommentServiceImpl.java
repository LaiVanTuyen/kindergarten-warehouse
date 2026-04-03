package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.response.CommentResponse;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.entity.Comment;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.CommentMapper;
import com.kindergarten.warehouse.repository.CommentRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    @Override
    @LogAction(action = AuditAction.CREATE, description = "Added comment", target = "COMMENT")
    public CommentResponse createComment(String resourceId, String username, String content, int rating) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setRating(rating);
        comment.setUser(user);
        comment.setResource(resource);

        Comment savedComment = commentRepository.save(comment);

        // Update average rating
        updateResourceRating(resource);

        return commentMapper.toResponse(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentsByResourceId(String resourceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return commentRepository.findByResourceId(resourceId, pageable)
                .map(commentMapper::toResponse);
    }

    @Override
    @LogAction(action = AuditAction.DELETE, description = "Deleted comment", target = "COMMENT")
    public void deleteComment(Long commentId, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        // Check permission: only owner or admin can delete
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.name().equals("ADMIN"));
        if (!comment.getUser().getId().equals(user.getId()) && !isAdmin) {
             throw new AppException(ErrorCode.FORBIDDEN);
        }

        Resource resource = comment.getResource();
        commentRepository.delete(comment);
        
        updateResourceRating(resource);
    }

    private void updateResourceRating(Resource resource) {
        Double avg = commentRepository.getAverageRatingByResourceId(resource.getId());
        resource.setAverageRating(avg != null ? avg : 0.0);
        resourceRepository.save(resource);
    }
}
