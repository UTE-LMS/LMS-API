package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.mapper.CloPiLinkMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CloPiLinkMapperImpl implements CloPiLinkMapper {

  @Override
  public CloPiLink toDocument(String syllabusId, CloPiLinkRequest request) {
    if (request == null) {
      return null;
    }
    return CloPiLink.builder()
        .programVersionId(request.getProgramVersionId())
        .syllabusId(syllabusId)
        .cloId(request.getCloId())
        .piId(request.getPiId())
        .weight(request.getWeight())
        .verification(Verification.builder().status(VerificationStatus.UNVERIFIED).build())
        .build();
  }

  @Override
  public CloPiLinkResponse toResponse(CloPiLink link) {
    if (link == null) {
      return null;
    }
    return CloPiLinkResponse.builder()
        .id(link.getId())
        .programVersionId(link.getProgramVersionId())
        .syllabusId(link.getSyllabusId())
        .cloId(link.getCloId())
        .piId(link.getPiId())
        .weight(link.getWeight())
        .verification(link.getVerification())
        .build();
  }

  @Override
  public List<CloPiLinkResponse> toResponseList(List<CloPiLink> links) {
    if (links == null || links.isEmpty()) {
      return Collections.emptyList();
    }
    List<CloPiLinkResponse> responses = new ArrayList<>(links.size());
    for (CloPiLink link : links) {
      responses.add(toResponse(link));
    }
    return responses;
  }
}
