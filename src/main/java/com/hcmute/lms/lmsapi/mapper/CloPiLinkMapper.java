package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import java.util.List;

public interface CloPiLinkMapper {

  CloPiLink toDocument(String syllabusId, CloPiLinkRequest request);

  CloPiLinkResponse toResponse(CloPiLink link);

  List<CloPiLinkResponse> toResponseList(List<CloPiLink> links);
}
