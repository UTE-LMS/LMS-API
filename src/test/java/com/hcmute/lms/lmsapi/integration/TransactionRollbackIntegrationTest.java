package com.hcmute.lms.lmsapi.integration;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử tích hợp xác minh tính toàn vẹn giao dịch (ACID multi-document transaction)
 * trên MongoDB replica set (rs0).
 *
 * Tiêu chí nghiệm thu:
 * US-39/AC-2: Thao tác có @Transactional: nếu bước sau ném ngoại lệ thì cả dữ liệu nghiệp vụ
 * lẫn bản ghi nhật ký audit_logs của AuditService đều bị rollback, không có bản ghi mồ côi trong DB.
 */
@SpringBootTest
@Import(TransactionRollbackIntegrationTest.TransactionalBusinessTestService.class)
@DisplayName("US-39/AC-2: Transactional rollback on MongoDB replica set rs0")
public class TransactionRollbackIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionalBusinessTestService businessTestService;

    private static final String TEST_COLLECTION = "terms";
    private static final String FIXTURE_LABEL = "DỮ LIỆU THỬ NGHIỆM";

    private String testTermCode;

    @BeforeEach
    void setUp() {
        testTermCode = "TX_ROLLBACK_" + UUID.randomUUID().toString().substring(0, 8);
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        Query termQuery = Query.query(Criteria.where("code").is(testTermCode));
        mongoTemplate.remove(termQuery, TEST_COLLECTION);

        Query auditQuery = Query.query(Criteria.where("entity.collection").is(TEST_COLLECTION)
                .and("entity.id").is(testTermCode));
        mongoTemplate.remove(auditQuery, "audit_logs");
    }

    @Test
    @DisplayName("US-39/AC-2: Transactional rollback on MongoDB replica set rs0 - Rollback cả domain data và audit_logs khi có ngoại lệ")
    void testTransactionalRollbackWhenExceptionThrown() {
        // GẮN NHÃN DỮ LIỆU THỬ NGHIỆM
        String academicYear = FIXTURE_LABEL + " - Niên khóa 2026-2027";
        String auditReason = FIXTURE_LABEL + " - Thao tác thiết lập học kỳ thử nghiệm rollback";

        // Thực thi phương thức có @Transactional ném RuntimeException
        assertThatThrownBy(() -> businessTestService.executeFailingOperation(testTermCode, academicYear, auditReason))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(FIXTURE_LABEL);

        // 1. Kiểm tra xác nhận dữ liệu nghiệp vụ (domain document) trong collection 'terms' đã bị rollback (KHÔNG TỒN TẠI)
        Query termQuery = Query.query(Criteria.where("code").is(testTermCode));
        Document savedTerm = mongoTemplate.findOne(termQuery, Document.class, TEST_COLLECTION);
        assertThat(savedTerm)
                .as("DỮ LIỆU THỬ NGHIỆM: Bản ghi nghiệp vụ phải bị rollback hoàn toàn và không tồn tại trong collection 'terms'")
                .isNull();

        // 2. Kiểm tra xác nhận bản ghi audit log trong collection 'audit_logs' cũng đã bị rollback (KHÔNG CÓ BẢN GHI MỒ CÔI)
        List<AuditLog> auditLogs = auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc(TEST_COLLECTION, testTermCode);
        assertThat(auditLogs)
                .as("DỮ LIỆU THỬ NGHIỆM: Bản ghi nhật ký audit_logs phải bị rollback và không có bản ghi mồ côi trong CSDL")
                .isEmpty();
    }

    @Test
    @DisplayName("US-39/AC-2: Transactional commit on MongoDB replica set rs0 - Commit thành công cả domain data và audit_logs khi không có ngoại lệ")
    void testTransactionalCommitWhenSuccessful() {
        // GẮN NHÃN DỮ LIỆU THỬ NGHIỆM
        String academicYear = FIXTURE_LABEL + " - Niên khóa 2026-2027 Commit Thành Công";
        String auditReason = FIXTURE_LABEL + " - Thao tác thiết lập học kỳ thành công";

        // Thực thi phương thức có @Transactional hoàn thành trọn vẹn
        businessTestService.executeSuccessfulOperation(testTermCode, academicYear, auditReason);

        // 1. Kiểm tra xác nhận dữ liệu nghiệp vụ (domain document) trong collection 'terms' đã được commit lưu trữ
        Query termQuery = Query.query(Criteria.where("code").is(testTermCode));
        Document savedTerm = mongoTemplate.findOne(termQuery, Document.class, TEST_COLLECTION);
        assertThat(savedTerm)
                .as("DỮ LIỆU THỬ NGHIỆM: Bản ghi nghiệp vụ phải được lưu trữ trong collection 'terms' sau commit")
                .isNotNull();
        assertThat(savedTerm.getString("code")).isEqualTo(testTermCode);
        assertThat(savedTerm.getString("academicYear")).isEqualTo(academicYear);

        // 2. Kiểm tra xác nhận bản ghi audit log trong collection 'audit_logs' cũng đã được commit thành công
        List<AuditLog> auditLogs = auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc(TEST_COLLECTION, testTermCode);
        assertThat(auditLogs)
                .as("DỮ LIỆU THỬ NGHIỆM: Bản ghi audit_logs phải được lưu trữ đồng bộ trong cùng transaction")
                .hasSize(1);

        AuditLog logEntry = auditLogs.get(0);
        assertThat(logEntry.getAction()).isEqualTo(AuditAction.RULE_CONFIRM);
        assertThat(logEntry.getEntity().getCollection()).isEqualTo(TEST_COLLECTION);
        assertThat(logEntry.getEntity().getId()).isEqualTo(testTermCode);
        assertThat(logEntry.getReason()).isEqualTo(auditReason);
    }

    /**
     * Dịch vụ thử nghiệm mô phỏng nghiệp vụ thực tế tham gia giao dịch MongoDB đa tài liệu (@Transactional).
     */
    @TestComponent
    public static class TransactionalBusinessTestService {

        private final MongoTemplate mongoTemplate;
        private final AuditService auditService;

        public TransactionalBusinessTestService(MongoTemplate mongoTemplate, AuditService auditService) {
            this.mongoTemplate = mongoTemplate;
            this.auditService = auditService;
        }

        /**
         * Ghi dữ liệu nghiệp vụ và gọi AuditService, sau đó ném lỗi để kiểm tra rollback.
         */
        @Transactional(propagation = Propagation.REQUIRED)
        public void executeFailingOperation(String termCode, String academicYear, String auditReason) {
            // Bước 1: Ghi dữ liệu nghiệp vụ vào collection 'terms'
            Document termDoc = new Document("code", termCode)
                    .append("academicYear", academicYear)
                    .append("status", "OPEN");
            mongoTemplate.insert(termDoc, TEST_COLLECTION);

            // Bước 2: Ghi nhật ký kiểm toán vào audit_logs (cùng transaction)
            auditService.record(
                    AuditAction.RULE_CONFIRM,
                    AuditEntity.builder()
                            .collection(TEST_COLLECTION)
                            .id(termCode)
                            .build(),
                    null,
                    Map.of("academicYear", academicYear, "status", "OPEN"),
                    auditReason
            );

            // Bước 3: Ném ngoại lệ giữa chừng mô phỏng sự cố hệ thống
            throw new RuntimeException("DỮ LIỆU THỬ NGHIỆM: Mô phỏng ngoại lệ hệ thống ném ra sau khi ghi audit để kích hoạt rollback!");
        }

        /**
         * Ghi dữ liệu nghiệp vụ và gọi AuditService thành công.
         */
        @Transactional(propagation = Propagation.REQUIRED)
        public void executeSuccessfulOperation(String termCode, String academicYear, String auditReason) {
            // Bước 1: Ghi dữ liệu nghiệp vụ vào collection 'terms'
            Document termDoc = new Document("code", termCode)
                    .append("academicYear", academicYear)
                    .append("status", "OPEN");
            mongoTemplate.insert(termDoc, TEST_COLLECTION);

            // Bước 2: Ghi nhật ký kiểm toán vào audit_logs (cùng transaction)
            auditService.record(
                    AuditAction.RULE_CONFIRM,
                    AuditEntity.builder()
                            .collection(TEST_COLLECTION)
                            .id(termCode)
                            .build(),
                    null,
                    Map.of("academicYear", academicYear, "status", "OPEN"),
                    auditReason
            );
        }
    }
}
