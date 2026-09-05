package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.config.LastActiveFilter;
import com.kindergarten.warehouse.config.SecurityConfig;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.exception.GlobalExceptionHandler;
import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.security.JwtAuthenticationFilter;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ResourceService;
import com.rollbar.notifier.Rollbar;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test cho ranh giới giữa SecurityFilterChain và quyền nghiệp vụ.
 * Logic visibility chi tiết được kiểm ở ResourceAccessGuardTest và
 * ResourceSlugAccessWiringTest; lớp này khóa matcher GET /resources/** ở trạng
 * thái permitAll để 404/410 nghiệp vụ không bị thay bằng 401 của Spring Security.
 */
@SpringBootTest(
        classes = ResourceSlugSecurityIntegrationTest.TestApplication.class,
        properties = {
                "app.cors.allowed-origins=http://localhost",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@AutoConfigureMockMvc
class ResourceSlugSecurityIntegrationTest {

    private static final String SLUG = "tai-lieu-mau";
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, ResourceController.class, ViewerResolver.class,
            GlobalExceptionHandler.class})
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResourceService resourceService;

    @MockBean
    private MessageService messageService;

    @MockBean
    private Rollbar rollbar;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private LastActiveFilter lastActiveFilter;

    @BeforeEach
    void setUp() throws Exception {
        reset(resourceService, messageService);
        when(messageService.getMessage(anyString())).thenReturn("ok");

        // Hai filter là bean mock để test không cần JWT/Redis, nhưng vẫn phải
        // chuyển tiếp request qua chính SecurityFilterChain thật.
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(lastActiveFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Guest đi qua SecurityFilterChain và xem được PUBLIC")
    void guestCanReachPublicSlug() throws Exception {
        when(resourceService.getResourceBySlug(SLUG, Viewer.guest())).thenReturn(response());

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("res-1"));
    }

    @Test
    @DisplayName("Guest + INTERNAL/PRIVATE nhận 404 nghiệp vụ, không phải 401")
    void guestGetsBusinessNotFoundInsteadOfAuthenticationFailure() throws Exception {
        when(resourceService.getResourceBySlug(SLUG, Viewer.guest()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));

        verify(resourceService).getResourceBySlug(SLUG, Viewer.guest());
    }

    @Test
    @DisplayName("Giáo viên khác + PRIVATE nhận 404")
    void otherTeacherCannotSeePrivateResource() throws Exception {
        Viewer viewer = Viewer.of(OTHER_ID, Set.of(Role.TEACHER));
        when(resourceService.getResourceBySlug(SLUG, viewer))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG)
                        .with(authentication(as(OTHER_ID, Role.TEACHER))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chủ sở hữu xem được PRIVATE")
    void ownerCanSeeOwnPrivateResource() throws Exception {
        Viewer viewer = Viewer.of(OWNER_ID, Set.of(Role.TEACHER));
        when(resourceService.getResourceBySlug(SLUG, viewer)).thenReturn(response());

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG)
                        .with(authentication(as(OWNER_ID, Role.TEACHER))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Người có quyền xem tài liệu ARCHIVED nhận 410")
    void authorizedViewerGetsGoneForArchivedResource() throws Exception {
        Viewer viewer = Viewer.of(OWNER_ID, Set.of(Role.TEACHER));
        when(resourceService.getResourceBySlug(SLUG, viewer))
                .thenThrow(new AppException(ErrorCode.RESOURCE_ARCHIVED));

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG)
                        .with(authentication(as(OWNER_ID, Role.TEACHER))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(6010));
    }

    @Test
    @DisplayName("Matcher slug vẫn public: service được gọi cho request không đăng nhập")
    void slugMatcherRemainsPublicAndDelegatesAuthorizationToBusinessLayer() throws Exception {
        when(resourceService.getResourceBySlug(SLUG, Viewer.guest()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/resources/{slug}", SLUG))
                .andExpect(status().isNotFound());

        verify(resourceService).getResourceBySlug(SLUG, Viewer.guest());
    }

    @Test
    @DisplayName("Danh sách/search của guest truyền Viewer.guest xuống tầng truy vấn")
    void guestListDelegatesVisibilityToQueryLayer() throws Exception {
        when(resourceService.getPortalResources(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(Viewer.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/resources").param("keyword", "toan"))
                .andExpect(status().isOk());

        verify(resourceService).getPortalResources(any(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(Viewer.guest()));
    }

    @Test
    @DisplayName("Guest tải PUBLIC nhận mã 6011 từ guard, không bị matcher đổi thành 401 chung")
    void guestDownloadGetsDedicatedAuthenticationError() throws Exception {
        when(resourceService.getResourceFileInfo("res-1", Viewer.guest()))
                .thenThrow(new AppException(ErrorCode.DOWNLOAD_REQUIRES_AUTH));

        mockMvc.perform(get("/api/v1/resources/{id}/file", "res-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(6011));
    }

    @Test
    @DisplayName("Guest đoán id không tồn tại nhận 404 trước kiểm tra đăng nhập")
    void unknownDownloadIdDoesNotLeakThroughAuthenticationError() throws Exception {
        when(resourceService.getResourceFileInfo("missing", Viewer.guest()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/resources/{id}/file", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("GET resource route mới không được tự động public")
    void unknownNestedResourceRouteIsFailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/resources/res-1/future-endpoint"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1011));
    }

    private static ResourceResponse response() {
        ResourceResponse response = new ResourceResponse();
        response.setId("res-1");
        response.setTitle("Tai lieu mau");
        return response;
    }

    private static Authentication as(Long id, Role... roles) {
        CustomUserDetails details = CustomUserDetails.builder()
                .id(id)
                .username("nguoidung" + id)
                .roles(Set.of(roles).stream().map(Role::name)
                        .collect(java.util.stream.Collectors.toSet()))
                .enabled(true)
                .build();
        return new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
    }
}
