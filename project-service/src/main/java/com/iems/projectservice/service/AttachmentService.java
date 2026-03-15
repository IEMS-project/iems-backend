package com.iems.projectservice.service;

import com.iems.projectservice.entity.Attachment;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;

    public Attachment addAttachment(UUID issueId, String fileId, String fileName,
                                    String fileUrl, String fileType, Long fileSize, UUID uploadedBy) {
        Attachment att = new Attachment();
        att.setIssueId(issueId);
        att.setFileId(fileId);
        att.setFileName(fileName);
        att.setFileUrl(fileUrl);
        att.setFileType(fileType);
        att.setFileSize(fileSize);
        att.setUploadedBy(uploadedBy);
        return attachmentRepository.save(att);
    }

    public void deleteAttachment(UUID attachmentId) {
        Attachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ATTACHMENT_NOT_FOUND));
        attachmentRepository.delete(att);
    }

    public List<Attachment> getAttachmentsByIssue(UUID issueId) {
        return attachmentRepository.findByIssueId(issueId);
    }
}
