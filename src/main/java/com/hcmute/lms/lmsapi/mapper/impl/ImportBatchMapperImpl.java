package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.document.ImportRowError;
import com.hcmute.lms.lmsapi.document.ImportStats;
import com.hcmute.lms.lmsapi.dto.ImportBatchResponse;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.ImportRowErrorResponse;
import com.hcmute.lms.lmsapi.mapper.ImportBatchMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchMapperImpl implements ImportBatchMapper {

  @Override
  public ImportBatchResponse toResponse(ImportBatch batch) {
    if (batch == null) {
      return null;
    }
    return ImportBatchResponse.builder()
        .id(batch.getId())
        .sectionId(batch.getSectionId())
        .assessmentId(batch.getAssessmentId())
        .fileName(batch.getFileName())
        .fileChecksum(batch.getFileChecksum())
        .fileRef(batch.getFileRef())
        .sourceSystem(batch.getSourceSystem())
        .columnMapping(toDto(batch.getColumnMapping()))
        .emptyCellAs(batch.getEmptyCellAs())
        .reimportPolicy(batch.getReimportPolicy())
        .status(batch.getStatus())
        .stats(toStats(batch.getStats()))
        .uploadedBy(batch.getUploadedBy())
        .confirmedBy(batch.getConfirmedBy())
        .confirmedAt(batch.getConfirmedAt())
        .createdAt(batch.getCreatedAt())
        .build();
  }

  @Override
  public ImportRowErrorResponse toResponse(ImportRowError error) {
    if (error == null) {
      return null;
    }
    return ImportRowErrorResponse.builder()
        .rowNumber(error.getRowNumber())
        .column(error.getColumn())
        .errorType(error.getErrorType())
        .rawValue(error.getRawValue())
        .message(error.getMessage())
        .build();
  }

  @Override
  public ImportColumnMappingDto toDto(ImportColumnMapping mapping) {
    if (mapping == null) {
      return null;
    }
    List<ImportColumnMappingDto.Item> items =
        mapping.getItems().stream()
            .map(
                entry ->
                    ImportColumnMappingDto.Item.builder()
                        .column(entry.getColumn())
                        .item(entry.getItemId())
                        .build())
            .toList();
    return ImportColumnMappingDto.builder()
        .studentColumn(mapping.getStudentColumn())
        .items(items)
        .build();
  }

  private ImportBatchResponse.Stats toStats(ImportStats stats) {
    if (stats == null) {
      return null;
    }
    return ImportBatchResponse.Stats.builder()
        .totalRows(stats.getTotalRows())
        .validRows(stats.getValidRows())
        .errorRows(stats.getErrorRows())
        .scoreCount(stats.getScoreCount())
        .build();
  }
}
