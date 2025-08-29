package com.iems.reportservice.controller;

import com.iems.reportservice.dto.request.ReportRequestDto;
import com.iems.reportservice.dto.response.ApiResponseDto;
import com.iems.reportservice.dto.response.ReportResponseDto;
import com.iems.reportservice.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@Tag(name = "Report API", description = "Manage reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @PostMapping
    @Operation(summary = "Create report", description = "Create a new report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report created successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to create report")
    })
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> createReport(@RequestBody ReportRequestDto request) {
        ReportResponseDto dto = reportService.createReport(request);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Report created successfully", dto));
    }

    @GetMapping
    @Operation(summary = "Get all reports", description = "Retrieve all reports")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reports retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to get reports")
    })
    public ResponseEntity<ApiResponseDto<List<ReportResponseDto>>> getAllReports() {
        List<ReportResponseDto> reports = reportService.getAllReports();
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Reports retrieved successfully", reports));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get report by ID", description = "Retrieve report details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Report not found"),
            @ApiResponse(responseCode = "500", description = "Failed to get report")
    })
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> getReportById(@PathVariable UUID id) {
        Optional<ReportResponseDto> report = reportService.getReportById(id);
        return report.map(dto -> ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Report retrieved successfully", dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "Report not found", null)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update report", description = "Update an existing report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report updated successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to update report")
    })
    public ResponseEntity<ApiResponseDto<ReportResponseDto>> updateReport(@PathVariable UUID id,
                                                                          @RequestBody ReportRequestDto request) {
        ReportResponseDto dto = reportService.updateReport(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Report updated successfully", dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete report", description = "Delete a report by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report deleted successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to delete report")
    })
    public ResponseEntity<ApiResponseDto<Void>> deleteReport(@PathVariable UUID id) {
        reportService.deleteReport(id);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Report deleted successfully", null));
    }

    @GetMapping("/receiver/{receiverId}")
    @Operation(summary = "Get reports by receiver ID", description = "Retrieve reports assigned to a receiver")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reports retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to get reports")
    })
    public ResponseEntity<ApiResponseDto<List<ReportResponseDto>>> getReportsByReceiverId(@PathVariable UUID receiverId) {
        List<ReportResponseDto> reports = reportService.getAllReportsByReceiverId(receiverId);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Reports retrieved successfully by receiver ID", reports));
    }
}
