package com.kindergarten.warehouse.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogAction {
    String action(); // E.g. "DELETE", "CREATE", "UPDATE"

    String description() default ""; // Additional details pattern
}
