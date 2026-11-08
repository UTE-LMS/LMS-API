package com.hcmute.lms.lmsapi.exception;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("GlobalExceptionHandler: xung đột ghi đồng thời của MongoDB (mã 112) -> 409")
class WriteConflictHandlingTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ConflictController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static MongoCommandException mongoError(int code, String name) {
    BsonDocument response =
        new BsonDocument("ok", new BsonInt32(0))
            .append("code", new BsonInt32(code))
            .append("codeName", new BsonString(name))
            .append("errmsg", new BsonString(name));
    return new MongoCommandException(response, new ServerAddress("localhost", 27017));
  }

  @Test
  @DisplayName(
      "WriteConflict (112) bọc trong DataIntegrityViolationException -> 409 CONFLICT, không phải 500")
  void writeConflictIsAConflict() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/write-conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @DisplayName("Lỗi Mongo khác 112/121/11000 vẫn là 500 (không nuốt nhầm)")
  void otherMongoErrorsStayInternalErrors() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/other-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  @RestController
  static class ConflictController {

    @GetMapping("/write-conflict")
    public String writeConflict() {
      throw new DataIntegrityViolationException("conflict", mongoError(112, "WriteConflict"));
    }

    @GetMapping("/other-error")
    public String otherError() {
      throw new DataIntegrityViolationException("other", mongoError(13, "Unauthorized"));
    }
  }
}
