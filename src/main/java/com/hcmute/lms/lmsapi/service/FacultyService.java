package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.FacultyAssignPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyRequest;
import com.hcmute.lms.lmsapi.dto.FacultyResponse;
import com.hcmute.lms.lmsapi.dto.FacultyStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FacultyTransferPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import java.util.List;

public interface FacultyService {

  FacultyResponse createFaculty(FacultyRequest request);

  PageResponse<FacultyResponse> getFaculties(int page, int size, String search, Boolean isActive);

  FacultyResponse getFacultyById(String id);

  FacultyResponse updateFaculty(String id, FacultyUpdateRequest request);

  FacultyResponse updateFacultyStatus(String id, FacultyStatusUpdateRequest request);

  List<UserResponse> getProgramManagers(String facultyId);

  UserResponse assignProgramManager(String facultyId, FacultyAssignPmRequest request);

  void unassignProgramManager(String facultyId, String userId);

  UserResponse transferProgramManager(FacultyTransferPmRequest request);
}
