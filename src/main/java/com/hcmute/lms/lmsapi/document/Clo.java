package com.hcmute.lms.lmsapi.document;

import java.util.Map;
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
@Document(collection = "clos")
@CompoundIndexes({
  @CompoundIndex(
      name = "clos_syllabus_code_idx",
      def = "{'syllabusId': 1, 'code': 1}",
      unique = true)
})
public class Clo {

  @Id private String id;

  @Field(name = "syllabusId", targetType = FieldType.OBJECT_ID)
  private String syllabusId;

  @Field(name = "courseId", targetType = FieldType.OBJECT_ID)
  private String courseId;

  @Field("code")
  private String code;

  @Field("contentOriginal")
  private String contentOriginal;

  @Field("bloomLevel")
  private Integer bloomLevel;

  @Field("bloomNote")
  private String bloomNote;

  @Field("measurement")
  private CloMeasurement measurement;

  @Field("verification")
  private Verification verification;

  @Field("extra")
  private Map<String, Object> extra;
}
