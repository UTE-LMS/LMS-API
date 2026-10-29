package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.dto.PloResponse;

import java.util.List;

public interface PloMapper {

    PloResponse toResponse(Plo plo);

    List<PloResponse> toResponseList(List<Plo> plos);
}
