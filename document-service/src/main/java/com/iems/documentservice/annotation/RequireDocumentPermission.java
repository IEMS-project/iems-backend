package com.iems.documentservice.annotation;

import com.iems.documentservice.entity.enums.ProjectPermission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireDocumentPermission {
    ProjectPermission value();
}
