package com.iems.reportservice.service;

import com.iems.reportservice.dto.request.ReportRequestDto;
import com.iems.reportservice.dto.response.ReportReceiverResponse;
import com.iems.reportservice.dto.response.ReportResponseDto;
import com.iems.reportservice.entity.Report;
import com.iems.reportservice.entity.ReportReceiver;
import com.iems.reportservice.repository.ReportReceiverRepository;
import com.iems.reportservice.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportReceiverRepository reportReceiverRepository;


    // Create
    public ReportResponseDto createReport(ReportRequestDto request) {
        Report report = Report.builder()
                .title(request.getTitle())
                .fileUrl(request.getFileUrl())
                .createdBy(request.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .build();

        Report savedReport = reportRepository.save(report);

        List<ReportReceiver> reportReceivers = new ArrayList<>();
        if (request.getReceiverIds() != null) {
            request.getReceiverIds().forEach(userId -> {
                ReportReceiver receiver = new ReportReceiver();
                receiver.setReport(savedReport);
                receiver.setReceiverId(userId);
                receiver.setRead(false);
                reportReceiverRepository.save(receiver);
                reportReceivers.add(receiver);
            });
        }
        savedReport.setReportReceivers(reportReceivers);

        return convertToReportResponseDto(savedReport);
    }

    public List<ReportResponseDto> getAllReports() {
        return reportRepository.findAll()
                .stream()
                .map(this::convertToReportResponseDto)
                .collect(Collectors.toList());
    }

    public Optional<ReportResponseDto> getReportById(UUID id) {
        return reportRepository.findById(id)
                .map(this::convertToReportResponseDto);
    }

    public ReportResponseDto updateReport(UUID id, ReportRequestDto request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        report.setTitle(request.getTitle());
        report.setFileUrl(request.getFileUrl());

        Report updated = reportRepository.save(report);
        return convertToReportResponseDto(updated);
    }

    public void deleteReport(UUID id) {
        if (!reportRepository.existsById(id)) {
            throw new RuntimeException("Report not found");
        }
        reportRepository.deleteById(id);
    }

    public List<ReportResponseDto> getAllReportsByReceiverId(UUID receiverId) {
        return reportRepository.findAll()
                .stream()
                .map(this::convertToReportResponseDto)
                .filter(report -> report.getReceivers() != null &&
                        report.getReceivers().stream()
                                .anyMatch(r -> r.getReceiverId().equals(receiverId)))
                .collect(Collectors.toList());
    }


    public ReportResponseDto convertToReportResponseDto(Report report) {
        ReportResponseDto dto = new ReportResponseDto();
        dto.setId(report.getId());
        dto.setTitle(report.getTitle());
        dto.setCreatedBy(report.getCreatedBy());
        dto.setCreatedAt(report.getCreatedAt());

        dto.setReceivers(
                report.getReportReceivers()
                        .stream()
                        .map(this::convertToReportReceiverResponseDto)
                        .collect(Collectors.toList())
        );

        return dto;
    }

    public ReportReceiverResponse convertToReportReceiverResponseDto(ReportReceiver reportReceiver) {
        if (reportReceiver == null) {
            return null;
        }
        return new ReportReceiverResponse(
                reportReceiver.getId(),
                reportReceiver.getReceiverId(),
                reportReceiver.isRead(),
                reportReceiver.getReadAt()
        );
    }
}
