package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ImportBatchCreateRequest;
import com.hcmute.lms.lmsapi.dto.ImportBatchResponse;
import com.hcmute.lms.lmsapi.dto.ImportRowErrorResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** Nhập điểm từ tệp CSV (F05b, US-21..24). Quyền theo lớp được kiểm ở controller. */
public interface ImportBatchService {

  /**
   * Tải tệp, kiểm tra điều kiện nhập và cấp lô {@code UPLOADED} (US-21). Không tạo lô (không ghi
   * gì) khi: bài chưa APPROVED/SCORE_ENTERED, lớp/học kỳ khóa điểm, bài không phải ITEM, tệp đã
   * CONFIRMED trước đó (409), lỗi cấp tệp (400), ánh xạ cột sai.
   *
   * @param content nội dung tệp CSV (UTF-8)
   */
  ImportBatchResponse createBatch(
      String sectionId,
      String assessmentId,
      String fileName,
      byte[] content,
      ImportBatchCreateRequest request,
      UserPrincipal actor);

  /**
   * Kiểm tra từng dòng, ghi {@code import_row_errors} (thay lỗi của lần kiểm tra trước) và chuyển
   * lô sang VALIDATED hoặc HAS_ERRORS (US-22, US-23). Chạy lại được khi lô chưa xác nhận.
   */
  ImportBatchResponse validateBatch(String batchId, UserPrincipal actor);

  /**
   * Xác nhận lô VALIDATED trong một transaction: ghi toàn bộ scores, chuyển bài sang SCORE_ENTERED
   * nếu đây là lô đầu, chuyển lô sang CONFIRMED và ghi audit {@code IMPORT_CONFIRM}; lỗi giữa chừng
   * thì không ghi gì (US-24).
   */
  ImportBatchResponse confirmBatch(String batchId, UserPrincipal actor);

  /**
   * Hủy lô chưa xác nhận (UPLOADED, VALIDATED, HAS_ERRORS) thành ROLLED_BACK. Lô đã CONFIRMED chưa
   * được gỡ (cần quyết định về audit action và kiểm tra calculation_runs) nên trả 422.
   */
  ImportBatchResponse rollbackBatch(String batchId, UserPrincipal actor);

  ImportBatchResponse getBatch(String batchId);

  /** Các lô của một bài trong một lớp, mới nhất trước. */
  List<ImportBatchResponse> listBatches(String sectionId, String assessmentId);

  /** Lỗi theo dòng của lô, sắp theo số dòng. */
  PageResponse<ImportRowErrorResponse> getErrors(String batchId, Pageable pageable);
}
