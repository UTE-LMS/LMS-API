package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PiResponse;

import java.util.List;

public interface PiService {

    List<PiResponse> getPis(String programVersionId);

    PiResponse getPi(String programVersionId, String id);

    PiResponse createPi(String programVersionId, PiRequest request);

    PiResponse updatePi(String programVersionId, String id, PiRequest request);

    void deletePi(String programVersionId, String id);
}
