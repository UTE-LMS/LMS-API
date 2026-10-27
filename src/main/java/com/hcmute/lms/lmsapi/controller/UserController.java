package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.service.UserService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho phân hệ quản lý người dùng (F01 / US-02).
 * Toàn bộ các API đều yêu cầu quyền ADMIN; người dùng không có vai trò ADMIN sẽ bị từ chối với HTTP 403 Forbidden.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * Tạo mới một tài khoản người dùng (POST /api/v1/users).
     *
     * @param request dữ liệu yêu cầu tạo mới
     * @return UserResponse kèm HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("API request: POST /api/v1/users for username [{}]", request.getUsername());
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lấy danh sách người dùng có phân trang chuẩn hệ thống (GET /api/v1/users).
     *
     * @param page chỉ số trang (0-indexed, >= 0, mặc định 0)
     * @param size kích thước trang (1 <= size <= 100, mặc định 20)
     * @return PageResponse<UserResponse> kèm HTTP 200 OK
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0", required = false)
            @Min(value = 0, message = "Chỉ số trang (page) phải lớn hơn hoặc bằng 0") Integer page,
            @RequestParam(defaultValue = "20", required = false)
            @Min(value = 1, message = "Kích thước trang (size) phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Kích thước trang (size) phải nằm trong khoảng từ 1 đến 100") Integer size) {

        log.debug("API request: GET /api/v1/users with page [{}] and size [{}]", page, size);
        PageableValidationUtils.validate(page, size);
        PageResponse<UserResponse> response = userService.getUsers(page != null ? page : 0, size != null ? size : 20);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem chi tiết thông tin một người dùng theo ID (GET /api/v1/users/{id}).
     *
     * @param id định danh 24 ký tự hex (ObjectId)
     * @return UserResponse kèm HTTP 200 OK
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "Định dạng định danh (ObjectId) không hợp lệ") String id) {

        log.debug("API request: GET /api/v1/users/{}", id);
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật thông tin người dùng (PUT /api/v1/users/{id}).
     *
     * @param id định danh người dùng cần cập nhật
     * @param request dữ liệu cập nhật
     * @param principal thông tin người dùng đang thực hiện yêu cầu
     * @return UserResponse kèm HTTP 200 OK
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "Định dạng định danh (ObjectId) không hợp lệ") String id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        String currentUserId = principal != null ? principal.getId() : null;
        log.info("API request: PUT /api/v1/users/{} by user [{}]", id, currentUserId);
        UserResponse response = userService.updateUser(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật trạng thái Khóa / Mở khóa của người dùng (PUT /api/v1/users/{id}/status).
     *
     * @param id định danh người dùng cần cập nhật trạng thái
     * @param request trạng thái mới (ACTIVE hoặc LOCKED)
     * @param principal thông tin người dùng đang thực hiện yêu cầu
     * @return UserResponse kèm HTTP 200 OK
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "Định dạng định danh (ObjectId) không hợp lệ") String id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        String currentUserId = principal != null ? principal.getId() : null;
        log.info("API request: PUT /api/v1/users/{}/status to [{}] by user [{}]", id, request.getStatus(), currentUserId);
        UserResponse response = userService.updateStatus(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }
}
