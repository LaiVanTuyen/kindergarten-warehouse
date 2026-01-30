package com.kindergarten.warehouse.dto.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateResult<T> {
    private T result;
    private String messageKey;
}
