package com.hcmute.lms.lmsapi.document;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "programs")
@CompoundIndexes({
  @CompoundIndex(name = "code_unique_idx", def = "{'code': 1}", unique = true),
  @CompoundIndex(name = "program_faculty_idx", def = "{'facultyId': 1}"),
  @CompoundIndex(name = "program_faculty_code_idx", def = "{'facultyId': 1, 'code': 1}")
})
public class Program {

  @Id private String id;

  @Field(name = "facultyId", targetType = FieldType.OBJECT_ID)
  private String facultyId;

  @Field("code")
  private String code;

  @Field("name")
  private String name;

  @Field("level")
  private String level;

  @Field("degree")
  private String degree;

  @Field("extra")
  private Map<String, Object> extra;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;
}
