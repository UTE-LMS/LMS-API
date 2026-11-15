package com.hcmute.lms.lmsapi.document;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Document đại diện cho Khoa quản lý đào tạo trong collection {@code faculties}. Là đơn vị chủ quản
 * sở hữu Chương trình đào tạo, Môn học và nơi phân công Program Manager (1 PM = 1 Khoa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "faculties")
@CompoundIndexes({
  @CompoundIndex(name = "faculty_code_unique_idx", def = "{'code': 1}", unique = true),
  @CompoundIndex(name = "faculty_name_unique_idx", def = "{'name': 1}", unique = true),
  @CompoundIndex(name = "faculty_active_idx", def = "{'isActive': 1}")
})
public class Faculty {

  @Id private String id;

  @Field("code")
  private String code;

  @Field("name")
  private String name;

  @Field("description")
  private String description;

  @Builder.Default
  @Field("isActive")
  private boolean isActive = true;

  @Field("createdAt")
  private Instant createdAt;

  @Field("updatedAt")
  private Instant updatedAt;
}
