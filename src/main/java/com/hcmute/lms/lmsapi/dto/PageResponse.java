package com.hcmute.lms.lmsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Envelope phản hồi phân trang chuẩn dùng chung cho toàn bộ hệ thống LMS-API (F01 / ADR 0003 / API-CONTRACT §5.1.3).
 *
 * @param <T> kiểu dữ liệu của các phần tử trong trang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * Mảng chứa danh sách đối tượng dữ liệu trong trang hiện tại.
     */
    @Builder.Default
    private List<T> content = new ArrayList<>();

    /**
     * Chỉ số trang hiện tại (0-indexed).
     */
    private int page;

    /**
     * Kích thước trang thực tế được áp dụng.
     */
    private int size;

    /**
     * Tổng số lượng bản ghi thỏa mãn điều kiện trong CSDL.
     */
    private long totalElements;

    /**
     * Tổng số trang (ceil(totalElements / size)). Khi totalElements = 0 thì totalPages = 0.
     */
    private int totalPages;

    /**
     * true nếu đây là trang cuối cùng (hoặc danh sách rỗng); false nếu vẫn còn trang tiếp theo.
     */
    private boolean last;

    /**
     * Khởi tạo PageResponse từ danh sách dữ liệu và thông tin phân trang thủ công.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int calculatedTotalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / (size > 0 ? size : 1));
        boolean isLast = totalElements == 0 || page >= calculatedTotalPages - 1;

        return PageResponse.<T>builder()
                .content(content != null ? content : new ArrayList<>())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(calculatedTotalPages)
                .last(isLast)
                .build();
    }

    /**
     * Chuyển đổi từ đối tượng Spring Data {@link Page} sang {@link PageResponse}.
     */
    public static <T> PageResponse<T> from(Page<T> springPage) {
        if (springPage == null) {
            return of(new ArrayList<>(), 0, 20, 0L);
        }
        return of(springPage.getContent(), springPage.getNumber(), springPage.getSize(), springPage.getTotalElements());
    }
}
