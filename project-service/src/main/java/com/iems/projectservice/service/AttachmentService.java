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

    /**
     * Adds attachment data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param issueId the issue id parameter
     * @param fileId the file id parameter
     * @param fileName the file name parameter
     * @param fileUrl the file url parameter
     * @param fileType the file type parameter
     * @param fileSize the file size parameter
     * @param uploadedBy the uploaded by parameter
     * @return the add attachment result
     */
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

    /**
     * Deletes attachment data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param attachmentId the attachment id parameter
     */
    public void deleteAttachment(UUID attachmentId) {
        Attachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ATTACHMENT_NOT_FOUND));
        attachmentRepository.delete(att);
    }

    /**
     * Retrieves attachment information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param issueId the issue id parameter
     * @return the matching result collection
     */
    public List<Attachment> getAttachmentsByIssue(UUID issueId) {
        return attachmentRepository.findByIssueId(issueId);
    }
}
