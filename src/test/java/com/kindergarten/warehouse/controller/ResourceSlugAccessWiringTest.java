package com.kindergarten.warehouse.controller;

import com.rollbar.notifier.Rollbar;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.exception.GlobalExceptionHandler;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.FavoriteRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.security.CustomUserDetails;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test <strong>wiring</strong> (component), <em>không phải</em> integration test.
 *
 * <p>Kiểm đúng một chuỗi:
 *
 * <pre>
 * HTTP GET /api/v1/resources/{slug}
 *   → ResourceController
 *   → ViewerResolver.resolve(Authentication)
 *   → ResourceServiceImpl.getResourceBySlug(slug, viewer)
 *   → ResourceAccessGuard.requireViewable(...)
 *   → GlobalExceptionHandler
 *   → 200 / 404 / 410
 * </pre>
 *
 * <p>Controller, resolver, service và guard đều là <strong>instance thật</strong>;
 * chỉ repository và mapper bị mock. Không cần database, MinIO hay Redis nên
 * chạy nhanh và không phụ thuộc hạ tầng.
 *
 * <h2>NHỮNG THỨ TEST NÀY KHÔNG CHẠY</h2>
 *
 * {@code standaloneSetup} chỉ dựng một {@code DispatcherServlet} tối giản, nên
 * <strong>bỏ qua hoàn toàn</strong>:
 *
 * <ul>
 *   <li>{@code SecurityFilterChain} và các matcher trong {@code SecurityConfig}
 *       — không xác nhận được endpoint slug có thật sự đi qua matcher công khai</li>
 *   <li>{@code JwtAuthenticationFilter} — {@code Authentication} ở đây được đặt
 *       thẳng vào request, không đi qua giải mã token</li>
 *   <li>Method security ({@code @PreAuthorize})</li>
 *   <li>Cấu hình exception handling và content negotiation thật của Spring context</li>
 * </ul>
 *
 * <p>Nói cách khác: test này chứng minh <em>logic phân quyền nghiệp vụ</em>
 * đúng, nhưng <strong>không</strong> chứng minh Spring Security cho request đi
 * tới được chỗ đó. Nếu ai đó đổi {@code SecurityConfig} thành
 * {@code .authenticated()} cho {@code /resources/**}, test này vẫn xanh trong
 * khi khách thật sẽ nhận 401 thay vì 404/410.
 *
 * <p>Phần đó cần một {@code @SpringBootTest} + {@code @AutoConfigureMockMvc}
 * riêng — xem BUSINESS_RULES_V1 §3.7 và §4.3.
 *
 * <p>{@code Authentication} truyền qua {@code .principal(...)}: Spring MVC
 * phân giải tham số kiểu {@link java.security.Principal}, mà
 * {@link Authentication} kế thừa từ đó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceSlugAccessWiringTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final String SLUG = "tai-lieu-mau";

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private Rollbar rollbar;

    @Spy
    private ResourceAccessGuard resourceAccessGuard = new ResourceAccessGuard();

    @InjectMocks
    private ResourceServiceImpl resourceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(messageService.getMessage(anyString())).thenReturn("ok");

        ResourceResponse response = new ResourceResponse();
        response.setId("res-1");
        response.setTitle("Tai lieu mau");
        when(resourceMapper.toResponse(any(), anyBoolean())).thenReturn(response);
        when(favoriteRepository.existsByUserIdAndResourceId(anyLong(), anyString()))
                .thenReturn(false);

        ResourceController controller =
                new ResourceController(resourceService, messageService, new ViewerResolver());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(messageService, rollbar))
                .build();
    }

    // --- dữ liệu ----------------------------------------------------------

    private void givenResource(Visibility visibility, ResourceStatus status) {
        Category category = new Category();
        category.setIsDeleted(false);
        category.setVisibility(Visibility.PUBLIC);

        Topic topic = new Topic();
        topic.setIsDeleted(false);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setCategory(category);

        Resource resource = new Resource();
        resource.setId("res-1");
        resource.setSlug(SLUG);
        resource.setTopic(topic);
        resource.setVisibility(visibility);
        resource.setStatus(status);
        resource.setIsDeleted(false);
        resource.setCreatedBy(OWNER_ID);

        when(resourceRepository.findBySlug(SLUG)).thenReturn(Optional.of(resource));
    }

    private static Authentication as(Long id, Role... roles) {
        CustomUserDetails details = CustomUserDetails.builder()
                .id(id)
                .username("nguoidung" + id)
                .roles(Set.of(roles).stream().map(Role::name).collect(java.util.stream.Collectors.toSet()))
                .enabled(true)
                .build();
        return new UsernamePasswordAuthenticationToken(details, null, List.of());
    }

    private MockHttpServletRequestBuilder requestAs(Authentication authentication) {
        MockHttpServletRequestBuilder builder = get("/api/v1/resources/{slug}", SLUG);
        return authentication == null ? builder : builder.principal(authentication);
    }

    // =====================================================================

    @Test
    @DisplayName("Khách + PUBLIC + đã duyệt → 200")
    void guestGetsPublicResource() throws Exception {
        givenResource(Visibility.PUBLIC, ResourceStatus.APPROVED);

        mockMvc.perform(requestAs(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("res-1"));
    }

    @Test
    @DisplayName("Khách + INTERNAL → 404, không phải 401/403")
    void guestGetsNotFoundForInternal() throws Exception {
        givenResource(Visibility.INTERNAL, ResourceStatus.APPROVED);

        mockMvc.perform(requestAs(null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("Khách + PUBLIC nhưng ARCHIVED → 410 kèm mã 6010")
    void guestGetsGoneForArchivedPublic() throws Exception {
        givenResource(Visibility.PUBLIC, ResourceStatus.ARCHIVED);

        mockMvc.perform(requestAs(null))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(6010));
    }

    @Test
    @DisplayName("Khách + INTERNAL đã ARCHIVED → 404, KHÔNG lộ bằng 410")
    void guestGetsNotFoundForArchivedInternal() throws Exception {
        givenResource(Visibility.INTERNAL, ResourceStatus.ARCHIVED);

        mockMvc.perform(requestAs(null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("Giáo viên khác + PRIVATE của người khác → 404 (hồi quy isPrivileged)")
    void otherTeacherCannotSeePrivateResource() throws Exception {
        givenResource(Visibility.PRIVATE, ResourceStatus.APPROVED);

        // Truoc day isPrivileged() tra true cho MOI TEACHER nen ca nay tra 200.
        mockMvc.perform(requestAs(as(OTHER_ID, Role.TEACHER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chủ sở hữu + PRIVATE của chính mình → 200")
    void ownerSeesOwnPrivateResource() throws Exception {
        givenResource(Visibility.PRIVATE, ResourceStatus.APPROVED);

        mockMvc.perform(requestAs(as(OWNER_ID, Role.TEACHER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin + PRIVATE → 200")
    void adminSeesPrivateResource() throws Exception {
        givenResource(Visibility.PRIVATE, ResourceStatus.APPROVED);

        mockMvc.perform(requestAs(as(99L, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Người dùng thường + INTERNAL → 200 (chỉ cần đăng nhập)")
    void authenticatedUserSeesInternal() throws Exception {
        givenResource(Visibility.INTERNAL, ResourceStatus.APPROVED);

        mockMvc.perform(requestAs(as(OTHER_ID, Role.USER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Giáo viên khác + tài liệu PENDING của người khác → 404")
    void otherTeacherCannotSeePendingResource() throws Exception {
        givenResource(Visibility.PUBLIC, ResourceStatus.PENDING);

        mockMvc.perform(requestAs(as(OTHER_ID, Role.TEACHER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Slug không tồn tại → 404")
    void unknownSlugIsNotFound() throws Exception {
        when(resourceRepository.findBySlug(SLUG)).thenReturn(Optional.empty());

        mockMvc.perform(requestAs(null))
                .andExpect(status().isNotFound());
    }
}
