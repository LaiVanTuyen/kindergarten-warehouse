package com.kindergarten.warehouse.dto;

import com.kindergarten.warehouse.dto.request.LessonPlanRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LessonPlanRequestValidationTest {
    @Test
    void aNullStepIsAValidationErrorRatherThanAnInternalServerError() {
        var request = new LessonPlanRequest("Kế hoạch", null, null, null, null,
                null, null, null, null, null, null, null, null,
                java.util.Arrays.asList((LessonPlanRequest.Step) null), null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).startsWith("steps[0]"));
        }
    }
}
