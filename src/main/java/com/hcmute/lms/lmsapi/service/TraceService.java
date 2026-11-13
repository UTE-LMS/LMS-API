package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ScoreTraceResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultTraceResponse;

/** Service truy vết ngược từ kết quả đo lường về điểm và dòng tệp CSV gốc (US-34). */
public interface TraceService {

  StudentResultTraceResponse traceStudentResult(String studentResultId);

  ScoreTraceResponse traceScore(String scoreId);
}
