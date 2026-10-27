package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * Phạm vi phụ trách của người dùng (CTĐT, học phần, lớp học phần).
 * Được sử dụng trong {@link RoleAssignment} để kiểm soát phân quyền theo phạm vi (F01 / ADR 0003).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserScope {

    /**
     * Danh sách ID chương trình đào tạo được phân công phụ trách.
     */
    @Builder.Default
    @Field(name = "programIds", targetType = org.springframework.data.mongodb.core.mapping.FieldType.OBJECT_ID)
    private List<String> programIds = new ArrayList<>();

    /**
     * Danh sách ID học phần (course) được phân công phụ trách.
     */
    @Builder.Default
    @Field(name = "courseIds", targetType = org.springframework.data.mongodb.core.mapping.FieldType.OBJECT_ID)
    private List<String> courseIds = new ArrayList<>();

    /**
     * Danh sách ID lớp học phần (section) được phân công phụ trách.
     */
    @Builder.Default
    @Field(name = "sectionIds", targetType = org.springframework.data.mongodb.core.mapping.FieldType.OBJECT_ID)
    private List<String> sectionIds = new ArrayList<>();

    /**
     * Định nghĩa Scope rỗng nghiêm ngặt (Strict Empty Scope per ADR 0003):
     * Scope chỉ rỗng khi cả 3 danh sách programIds, courseIds, sectionIds đều rỗng hoặc null.
     * Nếu có bất kỳ ID nào trong bất kỳ danh sách nào thì scope KHÔNG phải là rỗng.
     *
     * @return {@code true} nếu cả 3 danh sách đều rỗng hoặc null; {@code false} nếu có ít nhất 1 ID
     */
    public boolean isEmpty() {
        boolean noPrograms = programIds == null || programIds.isEmpty();
        boolean noCourses = courseIds == null || courseIds.isEmpty();
        boolean noSections = sectionIds == null || sectionIds.isEmpty();
        return noPrograms && noCourses && noSections;
    }
}
