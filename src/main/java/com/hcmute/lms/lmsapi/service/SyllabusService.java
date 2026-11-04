package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import java.util.List;

public interface SyllabusService {

  List<SyllabusResponse> getSyllabi(String courseId);

  SyllabusResponse getSyllabus(String id);

  SyllabusResponse createSyllabus(String courseId, SyllabusRequest request);

  SyllabusResponse updateStatus(String id, SyllabusStatus newStatus);
}
