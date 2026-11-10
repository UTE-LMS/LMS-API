package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.document.ImportRowError;
import com.hcmute.lms.lmsapi.dto.ImportBatchResponse;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.ImportRowErrorResponse;

public interface ImportBatchMapper {

  ImportBatchResponse toResponse(ImportBatch batch);

  ImportRowErrorResponse toResponse(ImportRowError error);

  /** Ánh xạ đã được xác định về {@code itemId} (document) sang DTO trả về. */
  ImportColumnMappingDto toDto(ImportColumnMapping mapping);
}
