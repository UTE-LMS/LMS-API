package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import java.util.List;

public interface SectionMapper {

  /** Dựng document mới từ yêu cầu; lecturerIds đã được service khử trùng lặp trước khi truyền. */
  CourseSection toDocument(SectionRequest request, List<String> lecturerIds);

  SectionResponse toResponse(CourseSection section);

  List<SectionResponse> toResponseList(List<CourseSection> sections);
}
