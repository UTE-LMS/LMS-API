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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "courses")
@CompoundIndexes({@CompoundIndex(name = "code_unique_idx", def = "{'code': 1}", unique = true)})
public class Course {

  @Id private String id;

  @Field("code")
  private String code;

  @Field("name")
  private String name;

  @Field("credits")
  private Integer credits;

  @Field("extra")
  private Map<String, Object> extra;
}
