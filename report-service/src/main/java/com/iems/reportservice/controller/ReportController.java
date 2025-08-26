package com.iems.reportservice.controller;

import com.iems.reportservice.dto.request.ReportRequestDto;
import com.iems.reportservice.dto.response.ApiResponseDto;
import com.iems.reportservice.dto.response.ReportResponseDto;
import com.iems.reportservice.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    // Create
    @PostMapping
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> createReport(@RequestBody ReportRequestDto request) {
        ReportResponseDto dto = reportService.createReport(request);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Tạo báo cáo thành công", dto));
    }

    // Get all
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ReportResponseDto>>> getAllReports() {
        List<ReportResponseDto> reports = reportService.getAllReports();
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Lấy tất cả báo cáo thành công", reports));
    }

    // Get by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> getReportById(@PathVariable UUID id) {
        Optional<ReportResponseDto> report = reportService.getReportById(id);
        return report.map(dto -> ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Lấy báo cáo thành công", dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "Không tìm thấy báo cáo", null)));
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> updateReport(@PathVariable UUID id,
                                                                          @RequestBody ReportRequestDto request) {
        ReportResponseDto dto = reportService.updateReport(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Cập nhật báo cáo thành công", dto));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteReport(@PathVariable UUID id) {
        reportService.deleteReport(id);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Xóa báo cáo thành công", null));
    }

    // Get reports by receiverId
    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<ApiResponseDto<List<ReportResponseDto>>> getReportsByReceiverId(@PathVariable UUID receiverId) {
        List<ReportResponseDto> reports = reportService.getAllReportsByReceiverId(receiverId);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Lấy báo cáo theo receiverId thành công", reports));
    }
}
