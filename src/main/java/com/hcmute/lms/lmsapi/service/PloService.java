package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PloRequest;
import com.hcmute.lms.lmsapi.dto.PloResponse;

import java.util.List;

public interface PloService {

    List<PloResponse> getPlos(String programVersionId);

    PloResponse getPlo(String programVersionId, String id);

    PloResponse createPlo(String programVersionId, PloRequest request);

    PloResponse updatePlo(String programVersionId, String id, PloRequest request);

    void deletePlo(String programVersionId, String id);
}
