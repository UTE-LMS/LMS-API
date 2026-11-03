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
@Document(collection = "clo_pi_links")
@CompoundIndexes({
  @CompoundIndex(name = "clo_pi_links_clo_pi_idx", def = "{'cloId': 1, 'piId': 1}", unique = true),
  @CompoundIndex(name = "clo_pi_links_pi_idx", def = "{'piId': 1}"),
  @CompoundIndex(name = "clo_pi_links_program_version_idx", def = "{'programVersionId': 1}"),
  @CompoundIndex(name = "clo_pi_links_syllabus_idx", def = "{'syllabusId': 1}")
})
public class CloPiLink {

  @Id private String id;

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Field(name = "syllabusId", targetType = FieldType.OBJECT_ID)
  private String syllabusId;

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  @Field(name = "piId", targetType = FieldType.OBJECT_ID)
  private String piId;

  @Field("weight")
  private Double weight;

  @Field("verification")
  private Verification verification;
}
