package com.iems.reportservice.repository;

import com.iems.reportservice.entity.ReportReceiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportReceiverRepository extends JpaRepository<ReportReceiver, UUID> {

    // Lấy tất cả người nhận theo report
    List<ReportReceiver> findByReportId(UUID reportId);

    // Lấy tất cả report mà user là người nhận
    List<ReportReceiver> findByReceiverId(UUID receiverId);

    // Lấy report chưa đọc của user
    List<ReportReceiver> findByReceiverIdAndIsReadFalse(UUID receiverId);
}
