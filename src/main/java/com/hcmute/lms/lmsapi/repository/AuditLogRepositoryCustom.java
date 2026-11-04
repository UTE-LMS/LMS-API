package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interface mở rộng cho các truy vấn động trên collection audit_logs. Áp dụng nguyên lý Open-Closed
 * Principle (OCP) và Dependency Inversion Principle (DIP): cho phép mở rộng hành vi truy vấn bằng
 * MongoTemplate mà không làm thay đổi interface chính.
 */
public interface AuditLogRepositoryCustom {

  /**
   * Tìm kiếm nhật ký kiểm toán theo các tiêu chí động với giới hạn bản ghi.
   *
   * @param collection Tên collection (tùy chọn)
   * @param entityId ID của thực thể (tùy chọn)
   * @param action Hành động kiểm toán (tùy chọn)
   * @param from Thời điểm bắt đầu (tùy chọn)
   * @param to Thời điểm kết thúc (tùy chọn)
   * @param limit Giới hạn số lượng bản ghi trả về
   * @return Danh sách AuditLog thỏa điều kiện, sắp xếp theo thời điểm giảm dần
   */
  List<AuditLog> findLogsByCriteria(
      String collection, String entityId, AuditAction action, Instant from, Instant to, int limit);

  /**
   * Tìm nhật ký theo các tiêu chí tùy chọn, có phân trang, mới nhất trước (F01 / US-04). Tiêu chí
   * {@code null} hoặc rỗng thì bỏ qua; các tiêu chí còn lại kết hợp bằng AND.
   *
   * @param userId ObjectId (hex) của người thực hiện
   * @param collection Tên collection của thực thể bị tác động ({@code entity.collection})
   * @param entityId ID thực thể bị tác động ({@code entity.id})
   * @param action Hành động kiểm toán
   * @param from Thời điểm bắt đầu (bao gồm)
   * @param to Thời điểm kết thúc (bao gồm)
   * @param pageable Thông tin phân trang (thứ tự sắp xếp do repository quyết định)
   */
  Page<AuditLog> searchLogs(
      String userId,
      String collection,
      String entityId,
      AuditAction action,
      Instant from,
      Instant to,
      Pageable pageable);
}
