package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.dto.CloPiMatrixResponse;
import java.util.List;

public interface CloPiLinkService {

  List<CloPiLinkResponse> getLinks(String syllabusId);

  CloPiLinkResponse createLink(String syllabusId, CloPiLinkRequest request);

  void deleteLink(String id);

  CloPiMatrixResponse getMatrix(String syllabusId, String programVersionId);
}
