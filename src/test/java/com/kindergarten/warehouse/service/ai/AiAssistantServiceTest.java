package com.kindergarten.warehouse.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindergarten.warehouse.dto.request.AiRequests;
import com.kindergarten.warehouse.dto.request.LessonPlanRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.entity.ContentSource;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.service.ResourceAvailabilityService;
import com.kindergarten.warehouse.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Safety rules of the assistant, independent of what the model answers. */
class AiAssistantServiceTest {

    private final Viewer teacher = Viewer.of(7L, Set.of(Role.TEACHER));

    private ClaudeGateway claude;
    private AiQuotaService quota;
    private ResourceService resourceService;
    private ResourceAvailabilityService availability;
    private AiAssistantService service;

    /** Responses the fake model returns, by schema type. */
    private final Map<Class<?>, Object> answers = new HashMap<>();
    private final List<String> prompts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        claude = mock(ClaudeGateway.class);
        quota = mock(AiQuotaService.class);
        resourceService = mock(ResourceService.class);
        availability = mock(ResourceAvailabilityService.class);
        AgeGroupRepository ageGroups = mock(AgeGroupRepository.class);
        AgeGroup age = new AgeGroup();
        age.setId(1L);
        age.setSlug("3-4-tuoi");
        age.setName("Mầm (3-4 tuổi)");
        when(ageGroups.findAll()).thenReturn(List.of(age));
        when(ageGroups.findById(1L)).thenReturn(java.util.Optional.of(age));
        when(claude.isEnabled()).thenReturn(true);
        when(quota.consume(7L)).thenReturn(29);
        when(claude.structured(anyString(), anyString(), any())).thenAnswer(inv -> {
            prompts.add(inv.getArgument(1));
            return answers.get(inv.<Class<?>>getArgument(2));
        });
        // Availability returns responses for every id that "exists" in the warehouse.
        when(availability.viewableResponses(any(), eq(teacher))).thenAnswer(inv -> {
            Map<String, ResourceResponse> out = new HashMap<>();
            for (String id : inv.<Collection<String>>getArgument(0)) {
                if (!id.startsWith("gone")) out.put(id, resource(id));
            }
            return out;
        });
        service = new AiAssistantService(claude, quota, resourceService, availability, ageGroups, new ObjectMapper());
    }

    private static ResourceResponse resource(String id) {
        ResourceResponse r = new ResourceResponse();
        r.setId(id);
        r.setTitle("Tài liệu " + id);
        r.setFileType("VIDEO");
        return r;
    }

    private void warehouseReturns(String... ids) {
        when(resourceService.getPortalResources(any(), any(), eq(teacher))).thenReturn(
                new org.springframework.data.domain.PageImpl<>(
                        java.util.Arrays.stream(ids).map(AiAssistantServiceTest::resource).toList()));
    }

    @Test
    void disabledAssistantRefusesWithoutSpendingQuota() {
        when(claude.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.search(new AiRequests.Search("bài hát"), teacher))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.AI_DISABLED);
        verify(quota, never()).consume(any());
    }

    @Test
    void searchKeepsOnlyCandidatesTheTeacherCanOpenAndDropsInventedIds() {
        warehouseReturns("song-1", "song-2");
        answers.put(AiSchemas.SearchIntent.class, new AiSchemas.SearchIntent(
                List.of("bài hát gia đình"), List.of("3-4-tuoi", "not-a-slug"), List.of("VIDEO", "HACK")));
        answers.put(AiSchemas.Ranking.class, new AiSchemas.Ranking(List.of(
                new AiSchemas.Pick("invented-id", "bịa"),
                new AiSchemas.Pick("song-2", "Bài hát về gia đình"),
                new AiSchemas.Pick("song-2", "trùng"))));

        var result = service.search(new AiRequests.Search("Tìm bài hát về gia đình cho trẻ 3–4 tuổi, có video"), teacher);

        assertThat(result.items()).extracting(i -> i.resource().getId()).containsExactly("song-2");
        assertThat(result.remainingToday()).isEqualTo(29);
        // The warehouse is searched as the teacher, with only valid filters.
        ArgumentCaptor<ResourceFilterRequest> filter = ArgumentCaptor.forClass(ResourceFilterRequest.class);
        verify(resourceService, org.mockito.Mockito.atLeastOnce()).getPortalResources(filter.capture(), any(), eq(teacher));
        assertThat(filter.getAllValues().get(0).getAgeSlugs()).containsExactly("3-4-tuoi");
        assertThat(filter.getAllValues().get(0).getTypes()).containsExactly("VIDEO");
    }

    @Test
    void draftDropsResourcesOutsideCandidatesAndClampsToPlanLimits() {
        warehouseReturns("color-1");
        answers.put(AiSchemas.SearchIntent.class, new AiSchemas.SearchIntent(List.of("màu sắc"), List.of(), List.of()));
        List<AiSchemas.DraftStep> manySteps = new ArrayList<>();
        manySteps.add(new AiSchemas.DraftStep("Khám phá", 500, "Cho trẻ xem tranh", List.of("color-1", "private-x")));
        for (int i = 0; i < 25; i++) manySteps.add(new AiSchemas.DraftStep(" ", 1, null, null));
        answers.put(AiSchemas.PlanDraft.class, new AiSchemas.PlanDraft(
                "x".repeat(300), 20, "Nhận biết màu", "Giấy\nBút màu", null, manySteps, "Giả định lớp có bàn"));

        var draft = service.draftPlan(new AiRequests.Draft("Soạn hoạt động 20 phút về màu sắc", 1L, 20, 25), teacher);

        assertThat(draft.title()).hasSize(200);
        assertThat(draft.steps()).hasSize(20);
        assertThat(draft.steps().get(0).durationMinutes()).isEqualTo(240);
        assertThat(draft.steps().get(0).resourceIds()).containsExactly("color-1");
        assertThat(draft.steps().get(0).resources()).extracting(ResourceResponse::getId).containsExactly("color-1");
        assertThat(draft.steps().get(1).title()).isEqualTo("Bước 2");
        assertThat(prompts.get(1)).contains("Sĩ số: 25 trẻ").contains("Mầm (3-4 tuổi)");
    }

    @Test
    void adjustCannotAddResourcesKeepsUnavailableRefsAndNeverSendsReflection() {
        LessonPlanRequest plan = new LessonPlanRequest("Màu sắc", null, null, 20, 25, "Nhận biết màu", "Giấy",
                null, "GHI NHẬN RIÊNG CỦA CÔ", null, LessonPlanStatus.DRAFT, null, null,
                List.of(new LessonPlanRequest.Step("Khám phá", 20, "Xem tranh", ContentSource.TEACHER,
                        List.of("pic-1", "gone-1"))), 3L);
        answers.put(AiSchemas.PlanDraft.class, new AiSchemas.PlanDraft("Màu sắc", 15, "Nhận biết màu", "Giấy",
                "Không có máy chiếu: dùng tranh in", List.of(
                        new AiSchemas.DraftStep("Khám phá", 15, "Xem tranh in", List.of("pic-1", "gone-1", "new-9"))),
                "Rút còn 15 phút"));

        var draft = service.adjustPlan(new AiRequests.Adjust("Rút xuống 15 phút", plan), teacher);

        assertThat(draft.steps().get(0).resourceIds()).containsExactly("pic-1", "gone-1");
        assertThat(draft.steps().get(0).resources()).extracting(ResourceResponse::getId).containsExactly("pic-1");
        assertThat(prompts.get(0)).doesNotContain("GHI NHẬN RIÊNG CỦA CÔ");
        // Unavailable resources are not described to the model (title not leaked).
        assertThat(prompts.get(0)).doesNotContain("Tài liệu gone-1");
    }
}
