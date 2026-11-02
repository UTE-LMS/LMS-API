package com.hcmute.lms.lmsapi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Tiền điều kiện của toàn bộ khung kiểm thử tích hợp (issue #52, US-39/AC-2): kết nối từ {@code
 * MONGODB_URI} phải trỏ tới MongoDB replica set có primary ghi được. Nếu trỏ nhầm vào mongod
 * standalone thì transaction đa document không chạy được và test này đỏ trước, báo rõ nguyên nhân
 * thay vì để các test nghiệp vụ lỗi khó hiểu.
 */
@DisplayName("US-39/AC-2: tiền điều kiện replica set của khung integration test")
class MongoReplicaSetPreconditionIntegrationTest extends IntegrationTestBase {

  @Autowired private MongoTransactionManager transactionManager;

  @Test
  @DisplayName("US-39/AC-2: MONGODB_URI trỏ tới replica set có primary ghi được (không phải standalone)")
  void connectionIsAWritableReplicaSetPrimary() {
    Document hello = mongo().executeCommand(new Document("hello", 1));

    assertThat(hello.getString("setName"))
        .as("setName có mặt khi mongod chạy với --replSet; thiếu nghĩa là standalone")
        .isNotBlank();
    assertThat(hello.getBoolean("isWritablePrimary")).isTrue();
  }

  @Test
  @DisplayName("US-39/AC-2: ứng dụng có MongoTransactionManager để @Transactional hoạt động")
  void transactionManagerIsConfigured() {
    assertThat(transactionManager).isNotNull();
  }
}
