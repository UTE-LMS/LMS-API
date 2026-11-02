package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.PiContributionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_pi_contributions")
@CompoundIndexes({
    @CompoundIndex(name = "program_version_course_pi_unique_idx", def = "{'programVersionId': 1, 'courseId': 1, 'piId': 1}", unique = true),
    @CompoundIndex(name = "pi_id_idx", def = "{'piId': 1}")
})
public class CoursePiContribution {

    @Id
    private String id;

    @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
    private String programVersionId;

    @Field(name = "courseId", targetType = FieldType.OBJECT_ID)
    private String courseId;

    @Field(name = "piId", targetType = FieldType.OBJECT_ID)
    private String piId;

    @Field("contribution")
    private PiContributionType contribution;

    @Field("verification")
    private Verification verification;
}
