package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.exception.PageableValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích kiểm tra tính hợp lệ của tham số phân trang chuẩn hệ thống (F01 / US-02 / API-CONTRACT §5.1).
 * <p>
 * Quy tắc:
 * - {@code page >= 0} (zero-indexed, mặc định 0).
 * - {@code 1 <= size <= 100} (mặc định 20, tối đa 100).
 * - Bất kỳ tham số nào ngoài biên đều bị từ chối với HTTP 400 VALIDATION_ERROR kèm danh sách fieldErrors.
 */
public final class PageableValidationUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    private PageableValidationUtils() {
        // Utility class không cho phép khởi tạo
    }

    /**
     * Xác thực giá trị tham số page và size.
     *
     * @param page chỉ số trang
     * @param size kích thước trang
     * @throws PageableValidationException nếu page < 0 hoặc size < 1 hoặc size > 100
     */
    public static void validate(Integer page, Integer size) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();

        if (page != null && page < 0) {
            fieldErrors.add(FieldErrorDetail.builder()
                    .field("page")
                    .message("Chỉ số trang (page) phải lớn hơn hoặc bằng 0")
                    .rejectedValue(page)
                    .build());
        }

        if (size != null && (size < MIN_SIZE || size > MAX_SIZE)) {
            fieldErrors.add(FieldErrorDetail.builder()
                    .field("size")
                    .message("Kích thước trang (size) phải nằm trong khoảng từ 1 đến 100")
                    .rejectedValue(size)
                    .build());
        }

        if (!fieldErrors.isEmpty()) {
            throw new PageableValidationException(fieldErrors);
        }
    }

    /**
     * Xác thực và tạo đối tượng {@link Pageable} với các giá trị mặc định nếu tham số là null.
     *
     * @param page chỉ số trang (null thì lấy mặc định 0)
     * @param size kích thước trang (null thì lấy mặc định 20)
     * @return đối tượng {@link Pageable} hợp lệ
     * @throws PageableValidationException nếu page hoặc size ngoài biên hợp lệ
     */
    public static Pageable createPageable(Integer page, Integer size) {
        int effectivePage = page != null ? page : DEFAULT_PAGE;
        int effectiveSize = size != null ? size : DEFAULT_SIZE;

        validate(effectivePage, effectiveSize);

        return PageRequest.of(effectivePage, effectiveSize);
    }
}
