package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hợp đồng DTO của API (plan README mục 4 và 5): controller không trả/nhận {@code document}, không
 * lộ {@code passwordHash}, không lộ băm BCrypt, và ma trận I/R/M không có "trọng số" (US-07/AC-4).
 *
 * <p>Tập khóa JSON kỳ vọng của F02 chép từ bảng trường trong {@code doc/plan/F02-ctdt-plo-pi.md}
 * mục 3, không suy ra từ lớp Response.
 */
@DisplayName("Hợp đồng DTO: không lộ document, không lộ passwordHash")
class ApiContractIntegrationTest extends IntegrationTestBase {

  private static final String DOCUMENT_PACKAGE = "com.hcmute.lms.lmsapi.document";
  private static final Set<String> FORBIDDEN_KEYS =
      Set.of("passwordHash", "password", "tokenVersion", "_id", "_class");
  private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$");
  private static final Pattern WEIGHT_LIKE =
      Pattern.compile("(?i)weight|coefficient|score|heso|trongso|ratio");

  @Autowired private ApplicationContext applicationContext;

  // ------------------------------------------------------------------ phản chiếu

  @Test
  @DisplayName("Hợp đồng: không controller nào trả về hoặc nhận @RequestBody là lớp document")
  void controllersNeverExposeDocumentTypes() {
    List<String> violations = new ArrayList<>();
    int inspectedEndpoints = 0;
    for (Object bean : applicationContext.getBeansWithAnnotation(RestController.class).values()) {
      Class<?> controller = AopUtils.getTargetClass(bean);
      if (!controller.getPackageName().equals("com.hcmute.lms.lmsapi.controller")) {
        continue;
      }
      for (Method method : controller.getDeclaredMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          continue;
        }
        inspectedEndpoints++;
        violations.addAll(documentTypesIn(controller, method));
      }
    }
    assertThat(inspectedEndpoints).as("số endpoint được kiểm tra").isGreaterThanOrEqualTo(25);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Hợp đồng (tự kiểm): bộ dò phát hiện được document nằm trong generic lồng nhau")
  void detectorFlagsDocumentTypes() throws Exception {
    Method leaky = Leaky.class.getDeclaredMethod("leak", User.class);
    assertThat(documentTypesIn(Leaky.class, leaky)).hasSize(2);
  }

  @SuppressWarnings("unused")
  private static final class Leaky {
    ResponseEntity<List<User>> leak(@RequestBody User body) {
      return null;
    }
  }

  private static List<String> documentTypesIn(Class<?> controller, Method method) {
    List<String> found = new ArrayList<>();
    collectDocuments(ResolvableType.forMethodReturnType(method, controller), found, "return");
    for (Parameter parameter : method.getParameters()) {
      if (parameter.isAnnotationPresent(RequestBody.class)) {
        collectDocuments(ResolvableType.forType(parameter.getParameterizedType()), found, "body");
      }
    }
    String owner = controller.getSimpleName() + "#" + method.getName() + ":";
    return found.stream().map(s -> owner + s).toList();
  }

  private static void collectDocuments(ResolvableType type, List<String> found, String where) {
    Class<?> raw = type.resolve();
    if (raw != null && raw.getName().startsWith(DOCUMENT_PACKAGE + ".")) {
      found.add(where + "=" + raw.getSimpleName());
    }
    for (ResolvableType generic : type.getGenerics()) {
      collectDocuments(generic, found, where);
    }
  }

  // ------------------------------------------------------------------ JSON F01

  @Test
  @DisplayName(
      "US-01/AC-4: phản hồi login, /me, /users không có passwordHash, tokenVersion hay băm BCrypt")
  void f01ResponsesDoNotLeakSecrets() throws Exception {
    String rawPassword = "Mat-khau-thu-nghiem-1";
    User user = fixtures.userWithPassword(Role.ADMIN, null, rawPassword);
    String admin = fixtures.bearer(user);

    List<String> bodies = new ArrayList<>();
    bodies.add(
        body(
            mockMvc.perform(
                postAs(
                    null,
                    "/api/v1/auth/login",
                    new LoginRequest(user.getUsername(), rawPassword)))));
    bodies.add(body(mockMvc.perform(getAs(admin, "/api/v1/auth/me"))));
    bodies.add(body(mockMvc.perform(getAs(admin, "/api/v1/users?size=100"))));
    bodies.add(body(mockMvc.perform(getAs(admin, "/api/v1/users/{id}", user.getId()))));

    for (String json : bodies) {
      assertThat(keysOf(json)).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
      assertThat(BCRYPT_HASH.matcher(json).find()).as("băm BCrypt trong phản hồi").isFalse();
      assertThat(json).doesNotContain(rawPassword);
    }
  }

  // ------------------------------------------------------------------ JSON F02

  @Test
  @DisplayName(
      "Hợp đồng F02: tập khóa JSON của CTĐT/phiên bản/PLO/PI/ma trận đúng bảng trường trong kế hoạch")
  void f02ResponsesMatchDocumentedFields() throws Exception {
    String bearer = fixtures.bearerFor(Role.QA, null);
    Program program = fixtures.program();
    ProgramVersion version = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    Plo plo = fixtures.plo(version, "PLO1", 1);
    Pi pi = fixtures.pi(version, plo, "PI1.1", 1);
    var contribution = fixtures.contribution(version, fixtures.course(), pi, PiContributionType.I);
    String pv = version.getId();

    String programUrl = "/api/v1/programs/{id}";
    String ploUrl = "/api/v1/program-versions/{v}/plos/{id}";
    String piUrl = "/api/v1/program-versions/{v}/pis/{id}";
    assertThat(topKeys(mockMvc.perform(getAs(bearer, programUrl, program.getId()))))
        .containsExactlyInAnyOrder("id", "code", "name", "level", "degree");
    assertThat(topKeys(mockMvc.perform(getAs(bearer, "/api/v1/program-versions/{id}", pv))))
        .containsExactlyInAnyOrder(
            "id",
            "programId",
            "version",
            "totalCredits",
            "durationYears",
            "applicableCohorts",
            "status",
            "verification");
    assertThat(topKeys(mockMvc.perform(getAs(bearer, ploUrl, pv, plo.getId()))))
        .containsExactlyInAnyOrder(
            "id",
            "programVersionId",
            "code",
            "displayOrder",
            "category",
            "contentOriginal",
            "contentSummary",
            "capabilityLevelRaw",
            "passThreshold",
            "verification",
            "reviewNote");
    assertThat(topKeys(mockMvc.perform(getAs(bearer, piUrl, pv, pi.getId()))))
        .containsExactlyInAnyOrder(
            "id",
            "programVersionId",
            "ploId",
            "code",
            "displayOrder",
            "nameOriginalTerm",
            "contentOriginal",
            "contentShort",
            "capabilityLevelRaw",
            "targetPassRate",
            "weight",
            "passThreshold",
            "verification",
            "reviewNote");

    String matrixJson =
        body(mockMvc.perform(getAs(bearer, "/api/v1/program-versions/{v}/pi-contributions", pv)));
    assertThat(topLevelKeys(matrixJson))
        .containsExactlyInAnyOrder(
            "id", "programVersionId", "courseId", "piId", "contribution", "verification");
    assertThat(keysOf(matrixJson)).contains("status", "verifiedBy", "verifiedAt", "note");
    assertThat(matrixJson).contains(contribution.getId());
  }

  @Test
  @DisplayName(
      "US-07/AC-4: không API nào trả trọng số suy từ I/R/M (khóa weight/score/coefficient/ratio)")
  void matrixNeverExposesWeights() throws Exception {
    String bearer = fixtures.bearerFor(Role.QA, null);
    ProgramVersion version =
        fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);
    Pi pi = fixtures.pi(version, fixtures.plo(version, "PLO1", 1), "PI1.1", 1);
    for (PiContributionType type : PiContributionType.values()) {
      fixtures.contribution(version, fixtures.course(), pi, type);
    }

    String json =
        body(
            mockMvc.perform(
                getAs(bearer, "/api/v1/program-versions/{v}/pi-contributions", version.getId())));

    assertThat(keysOf(json)).noneMatch(key -> WEIGHT_LIKE.matcher(key).find());
    // Giá trị của contribution phải còn là chữ cái I/R/M, không phải số.
    assertThat(json)
        .contains("\"contribution\":\"I\"", "\"contribution\":\"R\"", "\"contribution\":\"M\"");
  }

  // ------------------------------------------------------------------ tiện ích

  private String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
    return actions
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private Set<String> topKeys(org.springframework.test.web.servlet.ResultActions actions)
      throws Exception {
    return topLevelKeys(body(actions));
  }

  /** Khóa cấp ngoài cùng của đối tượng (hoặc của phần tử đầu tiên nếu là mảng). */
  private Set<String> topLevelKeys(String json) {
    Object parsed = objectMapper.readValue(json, Object.class);
    if (parsed instanceof List<?> list && !list.isEmpty()) {
      parsed = list.get(0);
    }
    Set<String> keys = new TreeSet<>();
    if (parsed instanceof Map<?, ?> map) {
      map.keySet().forEach(k -> keys.add(String.valueOf(k)));
    }
    return keys;
  }

  /** Mọi tên khóa xuất hiện ở bất kỳ độ sâu nào trong JSON. */
  private Set<String> keysOf(String json) {
    Set<String> keys = new TreeSet<>();
    collectKeys(objectMapper.readValue(json, Object.class), keys);
    return keys;
  }

  private static void collectKeys(Object node, Set<String> keys) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (k, v) -> {
            keys.add(String.valueOf(k));
            collectKeys(v, keys);
          });
    } else if (node instanceof Iterable<?> items) {
      items.forEach(item -> collectKeys(item, keys));
    }
  }
}
