package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import java.util.List;

public interface SyllabusMapper {

  Syllabus toDocument(String courseId, SyllabusRequest request);

  SyllabusResponse toResponse(Syllabus syllabus);

  List<SyllabusResponse> toResponseList(List<Syllabus> syllabi);
}
