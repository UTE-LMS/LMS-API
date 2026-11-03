package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.dto.CloUpdateRequest;
import java.util.List;

public interface CloService {

  List<CloResponse> getClos(String syllabusId);

  CloResponse getClo(String id);

  CloResponse createClo(String syllabusId, CloRequest request);

  CloResponse updateClo(String id, CloUpdateRequest request);

  void deleteClo(String id);
}
