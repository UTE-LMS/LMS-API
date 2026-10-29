package com.hcmute.lms.lmsapi.document;

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
@Document(collection = "pis")
@CompoundIndexes({
    @CompoundIndex(name = "program_version_code_unique_idx", def = "{'programVersionId': 1, 'code': 1}", unique = true),
    @CompoundIndex(name = "plo_id_idx", def = "{'ploId': 1}")
})
public class Pi {

    @Id
    private String id;

    @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
    private String programVersionId;

    @Field(name = "ploId", targetType = FieldType.OBJECT_ID)
    private String ploId;

    @Field("code")
    private String code;

    @Field("displayOrder")
    private Integer displayOrder;

    @Field("nameOriginalTerm")
    private String nameOriginalTerm;

    @Field("contentOriginal")
    private String contentOriginal;

    @Field("contentShort")
    private String contentShort;

    @Field("capabilityLevelRaw")
    private Integer capabilityLevelRaw;

    @Field("targetPassRate")
    private Double targetPassRate;

    @Field("verification")
    private Verification verification;

    @Field("reviewNote")
    private String reviewNote;
}
