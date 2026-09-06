package com.kindergarten.warehouse.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mỗi {@link ErrorCode} phải có message key trong <strong>cả hai</strong> bundle.
 *
 * <h2>Vì sao cần test này</h2>
 *
 * <p>{@code GlobalExceptionHandler} gọi {@code messageService.getMessage(...)}
 * ngay trong thân handler. Key thiếu ở bundle gốc ({@code messages.properties})
 * làm {@code MessageSource} ném {@code NoSuchMessageException} <em>bên trong</em>
 * handler — Spring không còn handler nào để cứu, nên trả <strong>500</strong>.
 *
 * <p>Nghĩa là một dòng properties sai có thể biến toàn bộ hợp đồng lỗi (400,
 * 404, 409, 410...) thành 500. Và test đơn vị bình thường KHÔNG bắt được: chúng
 * mock {@code MessageService}, nên bundle thật không bao giờ được đọc tới.
 *
 * <p>Đã xảy ra thật ngày 2026-09-06: một lệnh sed viết {@code \&} thay vì
 * {@code &} đã thay dòng {@code error.invalid.request} bằng ký tự {@code &}.
 * 135/135 unit test vẫn xanh; chỉ smoke test trên môi trường demo mới lộ ra.
 */
class ErrorCodeMessageKeyTest {

    private static final String BASE_BUNDLE = "/i18n/messages.properties";
    private static final String VI_BUNDLE = "/i18n/messages_vi.properties";

    @Test
    @DisplayName("Mọi ErrorCode có key trong bundle gốc — thiếu là 500 thay vì mã đúng")
    void everyErrorCodeHasKeyInBaseBundle() throws IOException {
        assertNoMissingKeys(BASE_BUNDLE);
    }

    @Test
    @DisplayName("Mọi ErrorCode có key trong bundle tiếng Việt")
    void everyErrorCodeHasKeyInVietnameseBundle() throws IOException {
        assertNoMissingKeys(VI_BUNDLE);
    }

    @Test
    @DisplayName("Không có giá trị rỗng hoặc chỉ toàn khoảng trắng")
    void noBlankMessages() throws IOException {
        for (String bundle : List.of(BASE_BUNDLE, VI_BUNDLE)) {
            Properties props = load(bundle);
            List<String> blank = new ArrayList<>();
            for (ErrorCode code : ErrorCode.values()) {
                String value = props.getProperty(code.getMessage());
                if (value != null && value.isBlank()) {
                    blank.add(code.name() + " -> " + code.getMessage());
                }
            }
            assertThat(blank)
                    .as("Key rỗng trong %s", bundle)
                    .isEmpty();
        }
    }

    private void assertNoMissingKeys(String bundle) throws IOException {
        Properties props = load(bundle);

        List<String> missing = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (!props.containsKey(code.getMessage())) {
                missing.add(code.name() + " -> '" + code.getMessage() + "'");
            }
        }

        assertThat(missing)
                .as("Thiếu key trong %s. Thêm dòng tương ứng vào file này.", bundle)
                .isEmpty();
    }

    private Properties load(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("Không tìm thấy %s trên classpath", path).isNotNull();
            // Properties.load(InputStream) đọc theo ISO-8859-1; các file này là
            // UTF-8 nên phải dùng Reader, nếu không tiếng Việt sẽ thành mojibake.
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }
}
