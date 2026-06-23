package com.iems.documentservice.aop;

import com.iems.documentservice.annotation.RequireDocumentPermission;
import com.iems.documentservice.client.ProjectServiceFeignClient;
import com.iems.documentservice.entity.ProjectDocument;
import com.iems.documentservice.entity.enums.ProjectPermission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.ProjectDocumentRepository;
import com.iems.documentservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DocumentPermissionAspect {

    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectServiceFeignClient projectServiceFeignClient;

    @Before("@annotation(require)")
    public void checkPermission(JoinPoint jp, RequireDocumentPermission require) {
        UUID projectId = resolveProjectId(jp);
        UUID docId = resolveDocId(jp);
        UUID userId = resolveUserId();
        ProjectPermission permission = require.value();

        log.debug("Checking permission {} for user {} on doc {} or project {}", permission, userId, docId, projectId);

        // 1. Nếu có docId, kiểm tra quyền sở hữu trước
        if (docId != null) {
            ProjectDocument doc = projectDocumentRepository.findById(docId)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
            
            // Nếu là chủ sở hữu -> cho phép luôn (không cần check project permission)
            if (doc.getUploadedBy().equals(userId)) {
                return;
            }
            
            // Cập nhật projectId từ document nếu projectId truyền vào null
            if (projectId == null) {
                projectId = doc.getProjectId();
            }
        }

        // 2. Kiểm tra quyền trong project thông qua Project Service
        if (projectId == null) {
            log.warn("Permission check failed: Project ID could not be resolved");
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }

        try {
            var response = projectServiceFeignClient.checkPermission(projectId, permission.name());
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
            }
        } catch (Exception e) {
            log.error("Error calling project-service for permission check", e);
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    private UUID resolveDocId(JoinPoint jp) {
        Object val = resolveParam(jp, "docId");
        return val instanceof UUID ? (UUID) val : null;
    }

    private UUID resolveProjectId(JoinPoint jp) {
        Object val = resolveParam(jp, "projectId");
        return val instanceof UUID ? (UUID) val : null;
    }

    private Object resolveParam(JoinPoint jp, String name) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        String[] paramNames = sig.getParameterNames();
        Object[] args = jp.getArgs();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (name.equals(paramNames[i])) {
                    return args[i];
                }
            }
        }
        return null;
    }


    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails)) {
            // Spring Security should have handled this, but just in case
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        return userDetails.getUserId();
    }
}
