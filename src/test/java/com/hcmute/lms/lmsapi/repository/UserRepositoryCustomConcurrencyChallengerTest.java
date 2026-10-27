package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Empirical Challenger: Concurrency stress testing and edge-case validation
 * for UserRepositoryCustom.incrementTokenVersion ($inc atomicity and lost update protection).
 */
@SpringBootTest
@DisplayName("Empirical Challenge: UserRepositoryCustom.incrementTokenVersion Concurrency & Atomicity")
public class UserRepositoryCustomConcurrencyChallengerTest {

    private static final String FIXTURE_LABEL = "[DỮ LIỆU THỬ NGHIỆM]";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<String> createdUserIds = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        createdUserIds.clear();
    }

    @AfterEach
    void tearDown() {
        // Tự động dọn dẹp sạch sẽ các bản ghi thử nghiệm theo đúng ID đã tạo
        for (String id : createdUserIds) {
            if (ObjectId.isValid(id)) {
                mongoTemplate.remove(Query.query(new Criteria().orOperator(
                        Criteria.where("_id").is(id),
                        Criteria.where("_id").is(new ObjectId(id))
                )), "users");
            } else {
                mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), "users");
            }
        }
    }

    private User createFixtureUser(String customId, long initialTokenVersion) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .id(customId)
                .username("challenge_user_" + uniqueSuffix)
                .email("challenge_" + uniqueSuffix + "@hcmute.edu.vn")
                .passwordHash("$2a$10$dummyHashValueForChallengerVerificationOnly1234567890")
                .fullName(FIXTURE_LABEL + " Challenger Test User")
                .status(UserStatus.ACTIVE)
                .tokenVersion(initialTokenVersion)
                .roleAssignments(List.of(
                        RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(null)
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Stress Test: 100 concurrent threads incrementing tokenVersion must not suffer lost updates")
    void testConcurrentIncrementTokenVersion_NoLostUpdates() throws Exception {
        // Arrange: Tạo user với tokenVersion = 0
        String userId = new ObjectId().toHexString();
        createFixtureUser(userId, 0L);

        int totalThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulIncrements = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Chờ tất cả 100 luồng sẵn sàng và đồng loạt bắn request
                    startLatch.await();
                    long modified = userRepository.incrementTokenVersion(userId);
                    if (modified == 1L) {
                        successfulIncrements.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Chờ tất cả luồng đã sẵn sàng trước vạch xuất phát
        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        // Bắn súng xuất phát!
        startLatch.countDown();
        // Chờ tất cả luồng hoàn thành
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Assert: Cả 100 luồng đều phải cập nhật thành công (modified == 1)
        assertThat(successfulIncrements.get())
                .as("Mọi thao tác $inc đồng thời đều phải cập nhật thành công")
                .isEqualTo(totalThreads);

        // Đọc lại từ CSDL để kiểm chứng lost update: tokenVersion PHẢI ĐÚNG BẰNG 100
        User reloadedUser = userRepository.findById(userId).orElseThrow();
        assertThat(reloadedUser.getTokenVersion())
                .as("tokenVersion phải tăng tuyến tính chính xác từ 0 lên 100, tuyệt đối không bị lost update do tranh chấp đồng thời")
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("Sequential Test: 10 sequential increments must increase tokenVersion monotonically")
    void testSequentialIncrementTokenVersion_MonotonicGrowth() {
        String userId = new ObjectId().toHexString();
        createFixtureUser(userId, 0L);

        for (long expected = 1; expected <= 10; expected++) {
            long modified = userRepository.incrementTokenVersion(userId);
            assertThat(modified).isEqualTo(1L);

            User current = userRepository.findById(userId).orElseThrow();
            assertThat(current.getTokenVersion()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("Legacy Document Compatibility: incrementTokenVersion creates field if tokenVersion was absent")
    void testLegacyDocumentWithoutTokenVersionField() {
        // Mô phỏng document cũ chưa có thuộc tính tokenVersion trong CSDL
        String legacyId = new ObjectId().toHexString();
        createdUserIds.add(legacyId);

        Document legacyDoc = new Document("_id", new ObjectId(legacyId))
                .append("username", "legacy_user_" + UUID.randomUUID().toString().substring(0, 8))
                .append("email", "legacy_" + UUID.randomUUID().toString().substring(0, 8) + "@hcmute.edu.vn")
                .append("passwordHash", "$2a$10$legacyDummyHashedPassword1234567890")
                .append("fullName", FIXTURE_LABEL + " Legacy User")
                .append("status", "ACTIVE")
                .append("roleAssignments", List.of())
                .append("createdAt", Instant.now())
                .append("updatedAt", Instant.now());
        // Lưu ý: HOÀN TOÀN KHÔNG có trường tokenVersion

        mongoTemplate.insert(legacyDoc, "users");

        // Thực thi incrementTokenVersion
        long modified = userRepository.incrementTokenVersion(legacyId);
        assertThat(modified).isEqualTo(1L);

        // Kiểm tra document sau khi $inc: MongoDB tự tạo trường và gán giá trị 1
        Document updatedDoc = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(new ObjectId(legacyId))),
                Document.class,
                "users"
        );
        assertThat(updatedDoc).isNotNull();
        Number tokenVersionVal = (Number) updatedDoc.get("tokenVersion");
        assertThat(tokenVersionVal).isNotNull();
        assertThat(tokenVersionVal.longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Boundary Test: Non-existent, blank, and null user IDs must return 0 without error")
    void testBoundaryAndInvalidUserIds() {
        // Null ID
        assertThat(userRepository.incrementTokenVersion(null)).isEqualTo(0L);

        // Blank IDs
        assertThat(userRepository.incrementTokenVersion("")).isEqualTo(0L);
        assertThat(userRepository.incrementTokenVersion("   ")).isEqualTo(0L);

        // Non-existent random ObjectId
        String randomObjectId = new ObjectId().toHexString();
        assertThat(userRepository.incrementTokenVersion(randomObjectId)).isEqualTo(0L);

        // Non-existent arbitrary string ID
        assertThat(userRepository.incrementTokenVersion("non_existent_arbitrary_string_id_12345")).isEqualTo(0L);
    }

    @Test
    @DisplayName("ID Resolution Test: Works with both String ID and BSON ObjectId in MongoDB")
    void testStringAndObjectIdResolution() {
        // Case 1: ObjectId
        String objectId = new ObjectId().toHexString();
        createFixtureUser(objectId, 5L);

        long modified1 = userRepository.incrementTokenVersion(objectId);
        assertThat(modified1).isEqualTo(1L);
        User user1 = userRepository.findById(objectId).orElseThrow();
        assertThat(user1.getTokenVersion()).isEqualTo(6L);

        // Case 2: Custom non-ObjectId String format
        String stringId = "CUSTOM_STRING_ID_" + UUID.randomUUID().toString().substring(0, 8);
        createFixtureUser(stringId, 10L);

        long modified2 = userRepository.incrementTokenVersion(stringId);
        assertThat(modified2).isEqualTo(1L);
        User user2 = userRepository.findById(stringId).orElseThrow();
        assertThat(user2.getTokenVersion()).isEqualTo(11L);
    }
}
