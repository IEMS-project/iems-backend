package com.iems.projectservice.annotation;

import com.iems.projectservice.entity.enums.ProjectPermission;

import java.lang.annotation.*;

/**
 * Checks that the authenticated user has the given permission in the project
 * before the annotated controller method executes.
 *
 * The aspect resolves projectId from the method parameter named "projectId".
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireProjectPermission {
    ProjectPermission value();
}
