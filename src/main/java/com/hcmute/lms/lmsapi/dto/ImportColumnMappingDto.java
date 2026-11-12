package com.hcmute.lms.lmsapi.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportColumnMappingDto {

  private String studentColumn;

  @Builder.Default
  private List<Entry> items = new ArrayList<>();

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Entry {

    private String column;

    @JsonAlias({"itemId", "item"})
    @JsonProperty("item")
    private String item;

    private String cloId;

    public String getItemId() {
      return item;
    }

    public void setItemId(String itemId) {
      this.item = itemId;
    }

    public static class EntryBuilder {
      private String item;

      public EntryBuilder itemId(String itemId) {
        this.item = itemId;
        return this;
      }
    }
  }
}
