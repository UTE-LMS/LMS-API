package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import java.util.List;

public interface CloMapper {

  Clo toDocument(String syllabusId, String courseId, CloRequest request);

  CloResponse toResponse(Clo clo);

  List<CloResponse> toResponseList(List<Clo> clos);
}
