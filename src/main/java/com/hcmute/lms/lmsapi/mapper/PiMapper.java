package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.dto.PiResponse;

import java.util.List;

public interface PiMapper {

    PiResponse toResponse(Pi pi);

    List<PiResponse> toResponseList(List<Pi> pis);
}
