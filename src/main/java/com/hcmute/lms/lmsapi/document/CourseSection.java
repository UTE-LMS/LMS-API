package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.ArrayList;
import java.util.List;

/**
 * Document đại diện cho lớp học phần trong collection course_sections (§4.17 / ADR 0003).
 * Chỉ đọc trong phân hệ F01, phục vụ kiểm soát phân quyền theo phạm vi (canAccessSection).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_sections")
@CompoundIndexes({
    @CompoundIndex(name = "course_term_idx", def = "{'courseId': 1, 'termId': 1}"),
    @CompoundIndex(name = "lecturer_ids_idx", def = "{'lecturerIds': 1}")
})
public class CourseSection {

    @Id
    private String id;

    /**
     * Mã lớp học phần (duy nhất toàn hệ thống).
     */
    @Indexed(unique = true)
    @Field("code")
    private String code;

    /**
     * ID học phần tương ứng (collection courses).
     */
    @Field(name = "courseId", targetType = FieldType.OBJECT_ID)
    private String courseId;

    /**
     * ID đề cương áp dụng cho lớp học phần (collection syllabuses).
     */
    @Field(name = "syllabusId", targetType = FieldType.OBJECT_ID)
    private String syllabusId;

    /**
     * ID học kỳ mở lớp (collection terms).
     */
    @Field(name = "termId", targetType = FieldType.OBJECT_ID)
    private String termId;

    /**
     * Danh sách ID giảng viên phụ trách giảng dạy lớp học phần này (collection users).
     * Là nguồn phân quyền độc lập cho vai trò LECTURER per ADR 0003.
     */
    @Builder.Default
    @Field(name = "lecturerIds", targetType = FieldType.OBJECT_ID)
    private List<String> lecturerIds = new ArrayList<>();

    /**
     * Sĩ số sinh viên ghi danh vào lớp.
     */
    @Field("enrolledCount")
    private Integer enrolledCount;

    /**
     * Trạng thái lớp học phần (OPEN, CLOSED, LOCKED).
     */
    @Field("status")
    private String status;
}
