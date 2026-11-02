package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "program_versions")
@CompoundIndexes({
    @CompoundIndex(name = "program_version_unique_idx", def = "{'programId': 1, 'version': 1}", unique = true)
})
public class ProgramVersion {

    @Id
    private String id;

    @Field(name = "programId", targetType = FieldType.OBJECT_ID)
    private String programId;

    @Field("version")
    private String version;

    @Builder.Default
    @Field("applicableCohorts")
    private List<String> applicableCohorts = new ArrayList<>();

    @Field("totalCredits")
    private Integer totalCredits;

    @Field("durationYears")
    private Double durationYears;

    @Field("status")
    private ProgramVersionStatus status;

    @Field("verification")
    private Verification verification;

    @Field("extra")
    private Map<String, Object> extra;
}
