package com.iems.projectservice.aop;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.security.JwtUserDetails;
import com.iems.projectservice.service.ProjectPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionAspect {

    private final ProjectPermissionChecker permissionChecker;

    @Before("@annotation(require)")
    public void checkPermission(JoinPoint jp, RequireProjectPermission require) {
        UUID projectId = resolveProjectId(jp);
        UUID userId = resolveUserId();
        ProjectPermission permission = require.value();
        permissionChecker.requirePermission(projectId, userId, permission);
    }

    private UUID resolveProjectId(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Parameter[] params = sig.getMethod().getParameters();
        Object[] args = jp.getArgs();

        for (int i = 0; i < params.length; i++) {
            if ("projectId".equals(params[i].getName()) && args[i] instanceof UUID) {
                return (UUID) args[i];
            }
        }
        throw new IllegalStateException(
                "@RequireProjectPermission requires a method parameter named 'projectId' of type UUID");
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}
