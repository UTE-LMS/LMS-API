package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import java.util.List;

public interface SyllabusService {

  List<SyllabusResponse> getSyllabi(String courseId);

  default List<SyllabusResponse> getSyllabi(
      String courseId, SyllabusStatus status, String effectiveFromTermId, String search) {
    return getSyllabi(courseId);
  }

  default List<SyllabusResponse> getSyllabiByProgramVersion(
      String programVersionId,
      String courseId,
      SyllabusStatus status,
      String effectiveFromTermId,
      String search) {
    return List.of();
  }

  SyllabusResponse getSyllabus(String id);

  SyllabusResponse createSyllabus(String courseId, SyllabusRequest request);

  SyllabusResponse updateStatus(String id, SyllabusStatus newStatus);
}
