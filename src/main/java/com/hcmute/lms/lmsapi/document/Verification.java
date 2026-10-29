package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Verification {

    @Field("status")
    private VerificationStatus status;

    @Field(name = "verifiedBy", targetType = FieldType.OBJECT_ID)
    private String verifiedBy;

    @Field("verifiedAt")
    private Instant verifiedAt;

    @Field("note")
    private String note;
}
