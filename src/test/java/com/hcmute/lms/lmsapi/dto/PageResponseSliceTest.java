package com.hcmute.lms.lmsapi.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.PageableValidationException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PageResponse.slice")
class PageResponseSliceTest {

  private static final List<Integer> ALL = IntStream.rangeClosed(1, 25).boxed().toList();

  @Test
  @DisplayName("trang giữa: đúng phần tử, totalPages = 3, chưa phải trang cuối")
  void middlePage() {
    PageResponse<String> p =
        PageResponse.slice(ALL, 1, 10, items -> items.stream().map(String::valueOf).toList());
    assertThat(p.getContent()).hasSize(10).first().isEqualTo("11");
    assertThat(p.getTotalElements()).isEqualTo(25);
    assertThat(p.getTotalPages()).isEqualTo(3);
    assertThat(p.isLast()).isFalse();
  }

  @Test
  @DisplayName("trang cuối chỉ còn phần dư")
  void lastPage() {
    PageResponse<Integer> p = PageResponse.slice(ALL, 2, 10, items -> items);
    assertThat(p.getContent()).containsExactly(21, 22, 23, 24, 25);
    assertThat(p.isLast()).isTrue();
  }

  @Test
  @DisplayName("page vượt quá tổng số trang: nội dung rỗng, totalElements giữ nguyên")
  void pageBeyondEnd() {
    PageResponse<Integer> p = PageResponse.slice(ALL, 9, 10, items -> items);
    assertThat(p.getContent()).isEmpty();
    assertThat(p.getTotalElements()).isEqualTo(25);
  }

  @Test
  @DisplayName("danh sách rỗng: totalPages = 0")
  void emptyList() {
    PageResponse<Integer> p = PageResponse.slice(List.<Integer>of(), 0, 20, items -> items);
    assertThat(p.getContent()).isEmpty();
    assertThat(p.getTotalPages()).isZero();
    assertThat(p.isLast()).isTrue();
  }

  @Test
  @DisplayName("mapper chỉ nhận phần tử của trang hiện tại")
  void mapperReceivesOnlyPageItems() {
    int[] seen = {0};
    PageResponse.slice(
        ALL,
        0,
        5,
        items -> {
          seen[0] = items.size();
          return items;
        });
    assertThat(seen[0]).isEqualTo(5);
  }

  @Test
  @DisplayName("size = 0, size > 100 hoặc page < 0 bị từ chối")
  void invalidParams() {
    assertThatThrownBy(() -> PageResponse.slice(ALL, 0, 0, items -> items))
        .isInstanceOf(PageableValidationException.class);
    assertThatThrownBy(() -> PageResponse.slice(ALL, 0, 101, items -> items))
        .isInstanceOf(PageableValidationException.class);
    assertThatThrownBy(() -> PageResponse.slice(ALL, -1, 10, items -> items))
        .isInstanceOf(PageableValidationException.class);
  }
}
