package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloMeasurement;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CloMeasurementDto;
import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CloMapperImpl implements CloMapper {

  @Override
  public Clo toDocument(String syllabusId, String courseId, CloRequest request) {
    if (request == null) {
      return null;
    }
    CloMeasurement measurement;
    if (request.getMeasurement() != null) {
      Double threshold = request.getMeasurement().getDefaultPassThreshold();
      measurement =
          CloMeasurement.builder()
              .defaultPassThreshold(threshold != null ? threshold : 0.5)
              .build();
    } else {
      measurement = CloMeasurement.builder().defaultPassThreshold(0.5).build();
    }

    return Clo.builder()
        .syllabusId(syllabusId)
        .courseId(courseId)
        .code(request.getCode() != null ? request.getCode().trim() : null)
        .contentOriginal(
            request.getContentOriginal() != null ? request.getContentOriginal().trim() : "")
        .bloomLevel(request.getBloomLevel())
        .bloomNote(request.getBloomNote() != null ? request.getBloomNote().trim() : null)
        .measurement(measurement)
        .verification(Verification.builder().status(VerificationStatus.UNVERIFIED).build())
        .extra(request.getExtra())
        .build();
  }

  @Override
  public CloResponse toResponse(Clo clo) {
    if (clo == null) {
      return null;
    }
    CloMeasurementDto measurementDto = null;
    if (clo.getMeasurement() != null) {
      measurementDto =
          CloMeasurementDto.builder()
              .defaultPassThreshold(clo.getMeasurement().getDefaultPassThreshold())
              .build();
    }

    return CloResponse.builder()
        .id(clo.getId())
        .syllabusId(clo.getSyllabusId())
        .courseId(clo.getCourseId())
        .code(clo.getCode())
        .contentOriginal(clo.getContentOriginal())
        .bloomLevel(clo.getBloomLevel())
        .bloomNote(clo.getBloomNote())
        .measurement(measurementDto)
        .verification(clo.getVerification())
        .extra(clo.getExtra())
        .build();
  }

  @Override
  public List<CloResponse> toResponseList(List<Clo> clos) {
    if (clos == null || clos.isEmpty()) {
      return Collections.emptyList();
    }
    List<CloResponse> responses = new ArrayList<>(clos.size());
    for (Clo clo : clos) {
      responses.add(toResponse(clo));
    }
    return responses;
  }
}
