package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng phân trang/sắp xếp theo API_CONTRACT_V2 §0.5.
 *
 * <p>Điểm quan trọng nhất mà bộ test này canh: field sort ngoài whitelist phải
 * <strong>ném lỗi</strong>, không được âm thầm rơi về field mặc định. Fallback
 * âm thầm là kiểu hỏng tệ nhất ở đây — client thấy 200, thấy dữ liệu, thấy mũi
 * tên sort trên đúng cột, nhưng thứ tự lại theo field khác. Không ai phát hiện
 * cho tới khi có người đối chiếu tay.
 */
class PageableUtilsTest {

    private static final Set<String> ALLOWED = Set.of("id", "createdAt", "title", "viewsCount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Nested
    @DisplayName("sanitizeSort")
    class SanitizeSort {

        @Test
        @DisplayName("Field trong whitelist đi qua nguyên vẹn, giữ đúng chiều")
        void allowedFieldPassesThrough() {
            Sort result = PageableUtils.sanitizeSort(
                    Sort.by(Sort.Direction.ASC, "title"), ALLOWED, DEFAULT_SORT);

            assertThat(result).containsExactly(Sort.Order.asc("title"));
        }

        @Test
        @DisplayName("Field ngoài whitelist ném 400, KHÔNG rơi về mặc định")
        void unknownFieldThrows() {
            assertThatThrownBy(() -> PageableUtils.sanitizeSort(
                    Sort.by("passwordHash"), ALLOWED, DEFAULT_SORT))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("Một field sai trong nhiều field cũng đủ để ném")
        void oneBadOrderAmongGoodOnesThrows() {
            assertThatThrownBy(() -> PageableUtils.sanitizeSort(
                    Sort.by("title").and(Sort.by("topicCount")), ALLOWED, DEFAULT_SORT))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Không truyền sort thì dùng mặc định của endpoint")
        void unsortedFallsBackToDefault() {
            assertThat(PageableUtils.sanitizeSort(Sort.unsorted(), ALLOWED, DEFAULT_SORT))
                    .isEqualTo(DEFAULT_SORT);
            assertThat(PageableUtils.sanitizeSort(null, ALLOWED, DEFAULT_SORT))
                    .isEqualTo(DEFAULT_SORT);
        }

        @Test
        @DisplayName("Whitelist rỗng chặn mọi field — fail-closed")
        void emptyWhitelistRejectsEverything() {
            assertThatThrownBy(() -> PageableUtils.sanitizeSort(
                    Sort.by("id"), Set.of(), DEFAULT_SORT))
                    .isInstanceOf(AppException.class);
        }
    }

    @Nested
    @DisplayName("sanitize")
    class SanitizePageable {

        @Test
        @DisplayName("size vượt trần bị kẹp về 100")
        void sizeIsCappedAtMax() {
            Pageable result = PageableUtils.sanitize(
                    PageRequest.of(0, 5_000, DEFAULT_SORT), ALLOWED, DEFAULT_SORT);

            assertThat(result.getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("Trang và size hợp lệ giữ nguyên")
        void validPageAndSizeSurvive() {
            Pageable result = PageableUtils.sanitize(
                    PageRequest.of(3, 25, Sort.by("viewsCount")), ALLOWED, DEFAULT_SORT);

            assertThat(result.getPageNumber()).isEqualTo(3);
            assertThat(result.getPageSize()).isEqualTo(25);
            assertThat(result.getSort()).containsExactly(Sort.Order.asc("viewsCount"));
        }

        @Test
        @DisplayName("Field sort sai làm hỏng cả lời gọi, không chỉ riêng phần sort")
        void invalidSortRejectsWholeRequest() {
            assertThatThrownBy(() -> PageableUtils.sanitize(
                    PageRequest.of(0, 10, Sort.by("resourceCount")), ALLOWED, DEFAULT_SORT))
                    .isInstanceOf(AppException.class);
        }
    }
}
