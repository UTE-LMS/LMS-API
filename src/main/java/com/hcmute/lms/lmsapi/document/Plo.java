package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.PloCategory;
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
@Document(collection = "plos")
@CompoundIndexes({
    @CompoundIndex(name = "program_version_code_unique_idx", def = "{'programVersionId': 1, 'code': 1}", unique = true)
})
public class Plo {

    @Id
    private String id;

    @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
    private String programVersionId;

    @Field("code")
    private String code;

    @Field("displayOrder")
    private Integer displayOrder;

    @Field("category")
    private PloCategory category;

    @Field("contentOriginal")
    private String contentOriginal;

    @Field("contentSummary")
    private String contentSummary;

    @Field("capabilityLevelRaw")
    private Integer capabilityLevelRaw;

    @Field("verification")
    private Verification verification;

    @Field("reviewNote")
    private String reviewNote;
}
