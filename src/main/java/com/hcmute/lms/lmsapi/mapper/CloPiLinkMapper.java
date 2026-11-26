package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import java.util.List;
import java.util.Map;

public interface CloPiLinkMapper {

  CloPiLink toDocument(String syllabusId, CloPiLinkRequest request);

  CloPiLinkResponse toResponse(CloPiLink link);

  CloPiLinkResponse toResponse(CloPiLink link, String piCode);

  List<CloPiLinkResponse> toResponseList(List<CloPiLink> links);

  List<CloPiLinkResponse> toResponseList(List<CloPiLink> links, Map<String, String> piCodeById);
}
