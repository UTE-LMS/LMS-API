package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AccessType;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller giả lập (Test Mock/Stub Controller) phục vụ kiểm thử tích hợp
 * và kiểm chứng ma trận phân quyền phạm vi canAccessSection (F01 / US-03 / ADR 0003).
 * Được đặt trong src/test/java để bảo đảm không xuất hiện trong mã nguồn runtime của ứng dụng.
 */
@RestController
@RequestMapping("/api/v1/test/sections")
@RequiredArgsConstructor
public class ScopeAuthorizationTestController {

    private final ScopeAuthorizationService scopeAuthorizationService;

    /**
     * Endpoint giả lập hành vi Đọc dữ liệu lớp học phần (READ).
     * Được bảo vệ bởi @PreAuthorize gọi canAccessSection với AccessType.READ.
     */
    @GetMapping("/{sectionId}")
    @PreAuthorize("@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, T(com.hcmute.lms.lmsapi.dto.AccessType).READ)")
    public ResponseEntity<Map<String, Object>> getSection(@PathVariable("sectionId") String sectionId) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "READ access granted",
                "sectionId", sectionId
        ));
    }

    /**
     * Endpoint giả lập hành vi Ghi dữ liệu lớp học phần (WRITE).
     * Được bảo vệ bởi @PreAuthorize gọi canAccessSection với AccessType.WRITE.
     */
    @PostMapping("/{sectionId}/grades")
    @PreAuthorize("@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, T(com.hcmute.lms.lmsapi.dto.AccessType).WRITE)")
    public ResponseEntity<Map<String, Object>> updateSectionGrades(@PathVariable("sectionId") String sectionId) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "WRITE access granted",
                "sectionId", sectionId
        ));
    }
}
