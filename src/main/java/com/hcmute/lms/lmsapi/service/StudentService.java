package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateResponse;
import com.hcmute.lms.lmsapi.dto.StudentResponse;
import com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import org.springframework.web.multipart.MultipartFile;

/** Quản lý danh sách sinh viên và cập nhật trạng thái hàng loạt cho Quản trị viên. */
public interface StudentService {

  PageResponse<StudentResponse> getStudents(
      int page, int size, String search, String cohort, StudentStatus status);

  StudentResponse updateStudentStatus(String id, StudentStatusUpdateRequest request);

  StudentBatchStatusUpdateResponse updateStudentsStatusBatch(
      StudentBatchStatusUpdateRequest request);

  AssessmentScoreTemplate generateTemplateCsv();

  CsvImportResultResponse importStudents(MultipartFile file);

  AssessmentScoreTemplate generateStatusTemplateCsv();

  CsvImportResultResponse importStudentStatuses(MultipartFile file);
}
