/**
 * Tầng Presentation (REST Controllers) của hệ thống LMS-API.
 *
 * <p>Quy chuẩn thiết kế theo kiến trúc phân tầng (Package-by-Layer) và các nguyên lý SOLID:
 * <ul>
 *   <li><b>Single Responsibility Principle (SRP):</b> Tầng Controller chỉ chịu trách nhiệm
 *       tiếp nhận HTTP requests, kiểm tra tính hợp lệ của DTO đầu vào (thông qua Bean Validation),
 *       chuyển tiếp lời gọi sang tầng Service, và đóng gói phản hồi HTTP. Tuyệt đối không chứa logic
 *       nghiệp vụ hay logic truy xuất dữ liệu.</li>
 *   <li><b>Dependency Inversion Principle (DIP):</b> Controller chỉ phụ thuộc vào các Business Service
 *       Interfaces (thuộc {@code com.hcmute.lms.lmsapi.service}), không phụ thuộc trực tiếp vào
 *       các lớp triển khai (.impl) hay tầng Data Access / Repository.</li>
 *   <li><b>Data Transfer Objects (DTO):</b> Giao tiếp vào/ra tầng Controller sử dụng các DTO
 *       (thuộc {@code com.hcmute.lms.lmsapi.dto}), không để lộ trực tiếp các MongoDB Documents ra ngoài.</li>
 *   <li><b>Xử lý lỗi tập trung:</b> Mọi ngoại lệ phát sinh được đón bắt và xử lý thống nhất bởi
 *       {@link com.hcmute.lms.lmsapi.exception.GlobalExceptionHandler}, đảm bảo định dạng JSON chuẩn
 *       {@link com.hcmute.lms.lmsapi.dto.ErrorResponse}.</li>
 * </ul>
 */
package com.hcmute.lms.lmsapi.controller;
