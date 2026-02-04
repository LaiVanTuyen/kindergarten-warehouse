package com.kindergarten.warehouse.aspect;

import com.kindergarten.warehouse.entity.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogAction {
    AuditAction action(); // E.g. CREATE, UPDATE, DELETE

    String description() default ""; // Additional details pattern

    String target() default ""; // Logical target name (e.g. "USER_BLOCK"). If empty, use method name.
}
