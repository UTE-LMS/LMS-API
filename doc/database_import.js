/*
 * ============================================================================
 *  KHỞI TẠO CƠ SỞ DỮ LIỆU MONGODB
 *  Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra CTĐT
 *  (Learning Outcomes Management and Analytics System)
 *  Thiết kế: GiaiThich_CoSoDuLieu.md v0.6 (v0.5 theo PLO_DB_UPDATE.md, v0.6 theo ADR 0013)
 * ============================================================================
 *
 *  CÁCH CHẠY (cần mongosh):
 *      mongosh "mongodb://localhost:27017" --file database_import.js
 *
 *  TÙY CHỌN (biến môi trường):
 *      LOMS_DB=ten_db        Tên cơ sở dữ liệu (mặc định: learning_outcomes)
 *      LOMS_SAMPLE=1         Nạp thêm dữ liệu MINH HỌA (CLO ví dụ của học phần OOP).
 *                            Mặc định KHÔNG nạp, để dữ liệu minh họa không lẫn với dữ liệu chính thức.
 *
 *  Ví dụ (PowerShell):
 *      $env:LOMS_DB = "learning_outcomes"; mongosh --file database_import.js
 *
 *  SCRIPT LÀM GÌ:
 *   1. Tạo 26 collection kèm quy tắc kiểm tra cấu trúc ($jsonSchema) và index.
 *   2. Nạp dữ liệu khởi tạo lấy từ tài liệu: 7 PLO, 17 PI, ma trận I/R/M của học phần
 *      OOPR230279 (trang 46) và khung quy tắc đo lường bản nháp.
 *   Hệ thống KHÔNG lưu nội dung câu hỏi/đề bài/đáp án (ADR 0013): không có ngân hàng câu hỏi;
 *   mỗi câu trong bài đánh giá chỉ có nhãn (Câu 1, Câu 2…) và điểm tối đa theo từng CLO.
 *
 *  AN TOÀN:
 *   - Chạy lại nhiều lần được: collection đã có thì chỉ cập nhật validator; dữ liệu khởi tạo
 *     chỉ chèn khi chưa tồn tại (không ghi đè nội dung đã được chỉnh sửa).
 *   - Script KHÔNG xóa bất kỳ dữ liệu nào.
 *   - Nội dung PLO/PI trong script là bản DIỄN GIẢI NGẮN lấy từ tài liệu hướng dẫn, chưa phải
 *     nguyên văn bản CTĐT; được đánh dấu NEEDS_CLARIFICATION cho tới khi đối chiếu bản gốc.
 * ============================================================================
 */

const DB_NAME = (typeof process !== 'undefined' && process.env.LOMS_DB) || 'learning_outcomes';
const LOAD_ILLUSTRATIVE_SAMPLE = (typeof process !== 'undefined' && process.env.LOMS_SAMPLE === '1');
const d = db.getSiblingDB(DB_NAME);
const NOW = new Date();

print('>>> Cơ sở dữ liệu: ' + DB_NAME + (LOAD_ILLUSTRATIVE_SAMPLE ? '  (có nạp dữ liệu minh họa)' : ''));

// ----------------------------------------------------------------------------
// 1. Kiểu dữ liệu dùng chung cho validator
// ----------------------------------------------------------------------------
const OID    = { bsonType: 'objectId' };
const OID_N  = { bsonType: ['objectId', 'null'] };
const STR    = { bsonType: 'string' };
const STR_N  = { bsonType: ['string', 'null'] };
const BOOL   = { bsonType: 'bool' };
const DATE   = { bsonType: 'date' };
const DATE_N = { bsonType: ['date', 'null'] };
const NUM    = { bsonType: ['double', 'int', 'long', 'decimal'] };
const NUM_N  = { bsonType: ['double', 'int', 'long', 'decimal', 'null'] };
const INT    = { bsonType: ['int', 'long', 'double'] };
const INT_N  = { bsonType: ['int', 'long', 'double', 'null'] };
const OBJ    = { bsonType: 'object' };
const OBJ_N  = { bsonType: ['object', 'null'] };
const arr    = (items) => ({ bsonType: 'array', items });
const en     = (...v) => ({ enum: v });
const bloom  = { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 6 };

const VERIFICATION = {
  bsonType: 'object',
  properties: { status: en('UNVERIFIED', 'VERIFIED', 'NEEDS_CLARIFICATION') }
};

const schema = (required, properties) => ({
  $jsonSchema: { bsonType: 'object', required: required, properties: properties }
});

// ----------------------------------------------------------------------------
// 2. Khai báo collection: validator + index
// ----------------------------------------------------------------------------
// CLO mà một câu hỏi/tiêu chí đo, kèm ĐIỂM TỐI ĐA riêng của CLO đó trong câu (> 0).
// ADR 0013: giảng viên nhập trực tiếp điểm tối đa và điểm đạt theo từng CLO của từng câu (không chia theo trọng số).
// ADR 0011 (Q10): câu hỏi chỉ gán CLO; PI suy ra từ kết quả CLO qua clo_pi_links.
const OUTCOME_MAPPING = {
  bsonType: 'object', required: ['cloId', 'maxScore'],
  properties: { cloId: OID, maxScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0, exclusiveMinimum: true } }
};
// Biểu thức: trong một câu hỏi, mỗi CLO chỉ khai báo một lần (tránh tính trùng điểm)
const noDuplicateClos = (mappingsPath) => ({ $eq: [ { $size: mappingsPath },
  { $size: { $setUnion: [ { $map: { input: mappingsPath, as: 'm', in: '$$m.cloId' } }, [] ] } } ] });

const SCORE_STATUS_VALUES = ['SCORED', 'BLANK', 'ABSENT', 'NOT_GRADED', 'EXEMPT', 'MISSING'];
const RESULT_LEVELS = ['CLO', 'COURSE', 'PI', 'PLO'];
// ADR 0013: giảng viên tự khóa ma trận đề (không qua duyệt); từ MATRIX_LOCKED trở đi khóa cấu trúc items[]
const ASSESSMENT_LOCKED_STATUSES = ['MATRIX_LOCKED', 'SCORE_ENTERED', 'LOCKED'];
const ASSESSMENT_NEED_ITEMS_STATUSES = ['MATRIX_LOCKED', 'SCORE_ENTERED', 'LOCKED'];
const rate01 = { bsonType: ['double', 'int', 'long', 'decimal', 'null'], minimum: 0, maximum: 1 };
// Mức năng lực/Bloom nguyên văn trong tài liệu: số nguyên 1–6
const level16 = { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 6 };

const COLLECTIONS = {

  // ----- Người dùng -----
  users: {
    validator: schema(['username', 'email', 'passwordHash', 'status', 'roleAssignments'], {
      username: STR, email: STR, passwordHash: STR, fullName: STR_N,
      status: en('ACTIVE', 'LOCKED'), tokenVersion: INT,
      roleAssignments: arr({
        bsonType: 'object', required: ['role'],
        properties: {
          role: en('ADMIN', 'PROGRAM_MANAGER', 'DEPARTMENT_HEAD', 'QA_OFFICER', 'LECTURER', 'STUDENT', 'VIEWER'),
          scope: {                           // { programIds, courseIds, sectionIds }
            bsonType: ['object', 'null'],
            properties: { programIds: arr(OID), courseIds: arr(OID), sectionIds: arr(OID) }
          }
        }
      })
    }),
    indexes: [[{ username: 1 }, { unique: true }], [{ email: 1 }, { unique: true }]]
  },

  // ----- CTĐT -----
  programs: {
    validator: schema(['code', 'name'], { code: STR, name: STR, level: STR_N, degree: STR_N, extra: OBJ_N }),
    indexes: [[{ code: 1 }, { unique: true }]]
  },

  program_versions: {
    validator: schema(['programId', 'version', 'status'], {
      programId: OID, version: STR, applicableCohorts: arr(STR),
      totalCredits: INT_N,                   // tổng số tín chỉ tốt nghiệp theo quy định
      durationYears: NUM_N,                  // thời gian đào tạo (năm)
      status: en('DRAFT', 'ACTIVE', 'ARCHIVED'),
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ programId: 1, version: 1 }, { unique: true }]]
  },

  plos: {
    validator: schema(['programVersionId', 'code', 'displayOrder', 'verification'], {
      programVersionId: OID,
      code: { bsonType: 'string', pattern: '^PLO[1-9][0-9]*$' },
      displayOrder: INT,
      category: { enum: ['GENERAL', 'SPECIALIZED', 'UNSPECIFIED', null] },  // chuẩn chung / chuẩn chuyên biệt
      contentOriginal: STR_N,            // nguyên văn trong CTĐT (null cho đến khi đối chiếu bản gốc)
      contentSummary: STR_N,             // diễn giải ngắn
      capabilityLevelRaw: level16,       // đúng số trong cột "Trình độ năng lực" (1–6), không tính từ PI
      verification: VERIFICATION, reviewNote: STR_N
    }),
    indexes: [[{ programVersionId: 1, code: 1 }, { unique: true }]]
  },

  pis: {
    validator: schema(['programVersionId', 'ploId', 'code', 'displayOrder', 'verification'], {
      programVersionId: OID, ploId: OID,
      code: { bsonType: 'string', pattern: '^PI[1-9][0-9]*\\.[1-9][0-9]*$' },
      displayOrder: INT,
      nameOriginalTerm: STR_N,
      contentOriginal: STR_N, contentShort: STR_N,
      capabilityLevelRaw: level16,
      targetPassRate: rate01,
      verification: VERIFICATION, reviewNote: STR_N
    }),
    indexes: [[{ programVersionId: 1, code: 1 }, { unique: true }], [{ ploId: 1 }]]
  },

  courses: {
    validator: schema(['code', 'name'], {
      code: STR, name: STR, credits: INT_N,
      extra: OBJ_N
    }),
    indexes: [[{ code: 1 }, { unique: true }]]
  },

  program_courses: {
    validator: schema(['programVersionId', 'courseId'], {
      programVersionId: OID, courseId: OID, semester: INT_N, credits: INT_N,
      type: { enum: ['COMPULSORY', 'ELECTIVE', null] },
      category: { enum: ['FOUNDATION', 'MAJOR_FOUNDATION', 'SPECIALIZED', 'CAPSTONE_PROJECT', 'THESIS', null] },
      prerequisiteCourseIds: arr(OID),
      previousCourseIds: arr(OID)
    }),
    indexes: [[{ programVersionId: 1, courseId: 1 }, { unique: true }]]
  },

  course_pi_contributions: {
    validator: schema(['programVersionId', 'courseId', 'piId', 'contribution'], {
      programVersionId: OID, courseId: OID, piId: OID,
      contribution: en('I', 'R', 'M'),
      verification: VERIFICATION
    }),
    indexes: [[{ programVersionId: 1, courseId: 1, piId: 1 }, { unique: true }], [{ piId: 1 }]]
  },

  // ----- Học phần, CLO, đánh giá -----
  syllabi: {
    validator: schema(['courseId', 'version', 'status'], {
      courseId: OID, version: STR,
      effectiveFromTermId: OID_N, effectiveToTermId: OID_N,
      status: en('DRAFT', 'CONFIRMED', 'ARCHIVED'),
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ courseId: 1, version: 1 }, { unique: true }]]
  },

  clos: {
    validator: schema(['syllabusId', 'courseId', 'code', 'contentOriginal'], {
      syllabusId: OID, courseId: OID,
      code: { bsonType: 'string', pattern: '^CLO[0-9]+$' },
      contentOriginal: STR, bloomLevel: bloom, bloomNote: STR_N,
      measurement: {                     // để trống (null) cho đến khi Khoa xác nhận
        bsonType: ['object', 'null'],
        properties: { defaultWeight: rate01, defaultPassThreshold: rate01 }
      },
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ syllabusId: 1, code: 1 }, { unique: true }]]
  },

  clo_pi_links: {
    validator: schema(['programVersionId', 'syllabusId', 'cloId', 'piId'], {
      programVersionId: OID, syllabusId: OID, cloId: OID, piId: OID,
      // tỷ trọng CLO → PI khi tổng hợp gián tiếp (pi.method = FROM_CLOS); 0 < x ≤ 1, null cho đến khi quy tắc được xác nhận
      weight: { bsonType: ['double', 'int', 'long', 'decimal', 'null'], minimum: 0, exclusiveMinimum: true, maximum: 1 },
      verification: VERIFICATION
    }),
    indexes: [[{ cloId: 1, piId: 1 }, { unique: true }], [{ piId: 1 }], [{ programVersionId: 1 }]]
  },

  assessments: {
    validator: {
      $and: [
        schema(['syllabusId', 'code', 'name', 'scope', 'dataGranularity', 'status', 'isLocked'], {
          syllabusId: OID, code: STR, name: STR,
          scope: en('SYLLABUS_TEMPLATE', 'SECTION'),
          sectionId: OID_N, templateId: OID_N,
          type: { enum: ['QUIZ', 'PRACTICAL', 'PROJECT', 'MIDTERM', 'FINAL', 'OTHER', null] },
          phase: { enum: ['FORMATIVE', 'MIDTERM', 'SUMMATIVE', null] },
          scheduledAt: DATE_N,
          // ADR 0013: không tính, không lưu tổng điểm bài. weightInCourse không tham gia công thức CLO (chờ Q20)
          weightInCourse: rate01,
          dataGranularity: en('ITEM', 'TOTAL_ONLY'),
          // ADR 0013: giảng viên tự khóa ma trận đề, rồi mới nhập điểm; xem GiaiThich §4.15
          status: en('DRAFT', 'MATRIX_LOCKED', 'SCORE_ENTERED', 'LOCKED'),
          isLocked: BOOL,                    // true khi status ∈ MATRIX_LOCKED, SCORE_ENTERED, LOCKED: cấm sửa items[]
          coverageSummary: {                 // thống kê độ phủ (điểm tối đa theo CLO) để giảng viên kiểm tra trước khi khóa
            bsonType: ['object', 'null'],
            properties: {
              totalItemsSum: NUM_N,
              byClo: arr({ bsonType: 'object', required: ['cloId'], properties: { cloId: OID, points: NUM_N, percentage: NUM_N } }),
              byBloom: arr({ bsonType: 'object', required: ['bloomLevel'],
                             properties: { bloomLevel: bloom, questionCount: INT_N, points: NUM_N, percentage: NUM_N } })
            }
          },
          exportedFiles: arr({
            bsonType: 'object',
            properties: { fileRef: STR_N, templateCode: STR_N, includeOutcomeLabels: BOOL, exportedBy: OID_N, exportedAt: DATE_N }
          }),
          items: arr({
            bsonType: 'object', required: ['_id', 'code', 'kind', 'outcomeMappings'],
            properties: {
              // code là NHÃN định danh (ví dụ 'Câu 1'); KHÔNG có trường lưu nội dung câu hỏi/đề bài/đáp án (ADR 0013)
              _id: OID, code: STR, orderIndex: INT_N,
              kind: en('QUESTION', 'CRITERION'),
              bloomLevel: bloom,
              // Bản SNAPSHOT tại thời điểm thi: mỗi CLO của câu kèm điểm tối đa riêng; điểm tối đa của câu = tổng, không lưu
              outcomeMappings: { bsonType: 'array', minItems: 1, items: OUTCOME_MAPPING },
              externalRef: STR_N,                          // nhãn cột điểm trên tệp nguồn để khớp khi nhập
              rubric: OBJ_N                                // { levels: [{ level, description, score }] }
            }
          }),
          source: OBJ_N, verification: VERIFICATION, extra: OBJ_N
        }),
        // Bản đánh giá theo lớp (scope=SECTION) bắt buộc có sectionId
        { $expr: { $or: [ { $ne: ['$scope', 'SECTION'] }, { $eq: [{ $type: '$sectionId' }, 'objectId'] } ] } },
        // isLocked khớp với trạng thái bài
        { $expr: { $eq: ['$isLocked', { $in: ['$status', ASSESSMENT_LOCKED_STATUSES] }] } },
        // Đã khóa ma trận trở đi thì phải có ít nhất một câu hỏi/tiêu chí
        { $expr: { $or: [ { $not: [{ $in: ['$status', ASSESSMENT_NEED_ITEMS_STATUSES] }] },
                          { $gte: [{ $size: { $ifNull: ['$items', []] } }, 1] } ] } },
        // Mỗi câu hỏi/tiêu chí: mỗi CLO chỉ khai báo một lần
        { $expr: { $allElementsTrue: [ { $map: { input: { $ifNull: ['$items', []] }, as: 'it',
            in: noDuplicateClos('$$it.outcomeMappings') } } ] } }
      ]
    },
    indexes: [
      [{ syllabusId: 1, sectionId: 1, code: 1 }, { unique: true }],
      [{ sectionId: 1, status: 1 }],
      [{ 'items.outcomeMappings.cloId': 1 }]
    ]
  },

  // ----- Học kỳ, lớp, sinh viên -----
  terms: {
    validator: schema(['code', 'status'], {
      code: STR, academicYear: STR_N, startDate: DATE_N, endDate: DATE_N,
      status: en('OPEN', 'SCORE_LOCKED', 'CLOSED')
    }),
    indexes: [[{ code: 1 }, { unique: true }]]
  },

  course_sections: {
    validator: schema(['code', 'courseId', 'syllabusId', 'termId', 'status'], {
      code: STR, courseId: OID, syllabusId: OID, termId: OID, lecturerIds: arr(OID),
      enrolledCount: INT_N, status: en('OPEN', 'CLOSED', 'LOCKED')
    }),
    indexes: [[{ code: 1 }, { unique: true }], [{ courseId: 1, termId: 1 }], [{ lecturerIds: 1 }]]
  },

  students: {
    validator: schema(['studentRef'], {
      studentRef: STR,
      userId: OID_N,                         // users._id khi sinh viên được cấp tài khoản xem kết quả của chính mình
      cohort: STR_N, majorCode: STR_N, programVersionId: OID_N,
      fullName: STR_N, email: STR_N,         // tùy chọn, chỉ lưu khi được phép (Q09)
      status: { enum: ['ACTIVE', 'SUSPENDED', 'GRADUATED', 'DROPPED_OUT', null] }
    }),
    indexes: [[{ studentRef: 1 }, { unique: true }], [{ programVersionId: 1, cohort: 1 }], [{ userId: 1 }]]
  },

  enrollments: {
    validator: schema(['sectionId', 'studentId', 'status'], {
      sectionId: OID, studentId: OID, status: en('ENROLLED', 'WITHDRAWN'), enrolledAt: DATE_N
    }),
    indexes: [[{ sectionId: 1, studentId: 1 }, { unique: true }]]
  },

  // ----- Nhập điểm -----
  import_batches: {
    validator: schema(['sectionId', 'assessmentId', 'sourceSystem', 'status'], {
      sectionId: OID, assessmentId: OID,
      fileName: STR_N, fileChecksum: STR_N, fileRef: STR_N,   // fileRef: đường dẫn lưu tệp gốc làm minh chứng
      sourceSystem: en('EXCEL', 'CSV', 'MOODLE_EXPORT'),
      columnMapping: OBJ_N,
      emptyCellAs: { enum: ['BLANK', 'MISSING', null] },
      reimportPolicy: { enum: ['REJECT_ON_CONFLICT', 'OVERWRITE', 'UPDATE_BLANK_ONLY', null] },
      status: en('UPLOADED', 'VALIDATED', 'HAS_ERRORS', 'CONFIRMED', 'ROLLED_BACK'),
      stats: OBJ_N, uploadedBy: OID_N, confirmedBy: OID_N, confirmedAt: DATE_N
    }),
    indexes: [[{ sectionId: 1, assessmentId: 1, createdAt: -1 }], [{ fileChecksum: 1 }]]
  },

  import_row_errors: {
    validator: schema(['batchId', 'rowNumber', 'errorType'], {
      batchId: OID, rowNumber: INT, column: STR_N,
      errorType: en('INVALID_STUDENT_CODE', 'SCORE_OUT_OF_RANGE', 'DUPLICATE_RECORD', 'MISSING_FIELD'),
      rawValue: STR_N, message: STR_N
    }),
    indexes: [[{ batchId: 1, rowNumber: 1 }]]
  },

  scores: {
    validator: {
      $and: [
        {
          $jsonSchema: {
            bsonType: 'object',
            required: ['sectionId', 'assessmentId', 'itemId', 'cloId', 'studentId', 'status', 'importBatchId'],
            properties: {
              // ADR 0013: một document là điểm của một sinh viên ở một CLO của một câu (ô câu × CLO)
              sectionId: OID, assessmentId: OID, itemId: OID, cloId: OID, studentId: OID,
              status: { enum: SCORE_STATUS_VALUES },
              rawScore: { bsonType: ['double', 'int', 'long', 'decimal', 'null'], minimum: 0 },
              maxScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0 },
              rubricLevel: { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 4 },
              importBatchId: OID, sourceRow: INT_N, revision: INT_N,
              updatedBy: OID_N, updatedAt: DATE_N
            },
            // SCORED/BLANK phải có rawScore + maxScore; các trạng thái còn lại rawScore phải là null
            oneOf: [
              { properties: { status: en('SCORED', 'BLANK'), rawScore: { bsonType: ['double', 'int', 'long', 'decimal'] } },
                required: ['rawScore', 'maxScore'] },
              { properties: { status: en('ABSENT', 'NOT_GRADED', 'EXEMPT', 'MISSING'), rawScore: { bsonType: 'null' } } }
            ]
          }
        },
        // rawScore <= maxScore (khi có điểm); BLANK (bỏ trống) phải bằng 0
        { $expr: { $or: [
            { $in: [{ $type: '$rawScore' }, ['null', 'missing']] },
            { $lte: ['$rawScore', '$maxScore'] } ] } },
        { $expr: { $or: [ { $ne: ['$status', 'BLANK'] }, { $eq: ['$rawScore', 0] } ] } }
      ]
    },
    indexes: [
      [{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1 }],
      [{ importBatchId: 1 }]
    ]
  },

  // ----- Đo lường -----
  measurement_rules: {
    validator: {
      $and: [
        schema(['code', 'version', 'status'], {
          code: STR, version: STR,
          status: en('DRAFT', 'CONFIRMED', 'RETIRED'),
          confirmedBy: OID_N, confirmedAt: DATE_N,
          scope: OBJ_N,
          // Các ngưỡng/phương pháp để null trong bản DRAFT cho đến khi Khoa xác nhận (Q04, Q12, Q13)
          clo: {
            bsonType: ['object', 'null'],
            properties: {
              method: { enum: ['WEIGHTED_ITEMS', 'POINTS_RATIO', null] },
              defaultPassThreshold: rate01, minCoverage: rate01
            }
          },
          course: {
            bsonType: ['object', 'null'],
            properties: { requireAllCloPass: { bsonType: ['bool', 'null'] }, defaultTargetPassRate: rate01 }
          },
          pi: {
            bsonType: ['object', 'null'],
            properties: {
              // ADR 0011: PI luôn suy ra từ kết quả CLO theo clo_pi_links.weight
              method: { enum: ['FROM_CLOS', null] },
              aggregationMethod: { enum: ['WEIGHTED_AVERAGE', 'MAX', 'LATEST', 'ACCUMULATED_EVIDENCE', null] },
              // ADR 0012: không quy đổi I/R/M thành hệ số; không có trường useIrmAsWeight/irmWeights
              defaultPassThreshold: rate01
            }
          },
          plo: {
            bsonType: ['object', 'null'],
            properties: {
              method: { enum: ['ALL_PIS_PASSED', 'WEIGHTED_PIS', 'AVERAGE_PIS', null] },
              defaultPassThreshold: rate01, cohortTargetPassRate: rate01
            }
          },
          missingDataPolicy: {
            bsonType: ['object', 'null'],
            properties: {
              blankScoreValue: { enum: [0] },   // BLANK luôn tính 0 (có ghi nhận)
              excludeAbsentFromDenominator: { bsonType: ['bool', 'null'] },
              excludeMissingFromDenominator: { bsonType: ['bool', 'null'] }
            }
          },
          notes: STR_N
        })
      ]
    },
    indexes: [[{ code: 1, version: 1 }, { unique: true }]]
  },

  calculation_runs: {
    validator: schema(['scope', 'ruleId', 'status', 'lockKey'], {
      scope: en('SECTION', 'COURSE', 'COHORT', 'PROGRAM'),
      scopeRef: {                            // đối tượng cụ thể của phạm vi (bổ sung để biết lượt tính áp dụng cho lớp/khóa nào)
        bsonType: ['object', 'null'],
        properties: { sectionIds: arr(OID), courseIds: arr(OID), cohorts: arr(STR), termId: OID_N }
      },
      lockKey: STR, programVersionId: OID_N, syllabusIds: arr(OID), ruleId: OID,
      isOfficial: BOOL,
      dataSnapshot: {
        bsonType: ['object', 'null'],
        properties: { scoreChecksum: STR_N, totalScores: INT_N, lastScoreUpdatedAt: DATE_N }
      },
      isStale: BOOL, staleReason: STR_N,
      status: en('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'),
      errorMessage: STR_N, triggeredBy: OID_N, startedAt: DATE_N, finishedAt: DATE_N
    }),
    indexes: [
      [{ scope: 1, ruleId: 1, startedAt: -1 }],
      [{ 'scopeRef.sectionIds': 1, startedAt: -1 }],
      // hai lượt tính cùng phạm vi + cùng quy tắc không được chạy đồng thời (khóa chỉ giữ khi đang chờ/đang xử lý)
      [{ lockKey: 1 }, { unique: true, partialFilterExpression: { status: { $in: ['PENDING', 'PROCESSING'] } }, name: 'uniq_running_lockKey' }]
    ]
  },

  student_results: {
    validator: schema(['runId', 'level', 'subjectId', 'studentId', 'status'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID,
      sectionId: OID_N, studentId: OID, termId: OID_N,
      status: en('COMPUTED', 'INSUFFICIENT_DATA', 'NOT_APPLICABLE'),
      value: rate01, passed: { bsonType: ['bool', 'null'] },
      evidenceCount: INT_N,
      completeness: {
        bsonType: ['object', 'null'],
        properties: { requiredCount: INT_N, availableCount: INT_N, ratio: rate01 }
      },
      // Truy vết một cấp liền kề: CLO → scores; PI → scores hoặc student_results (CLO); PLO → student_results (PI)
      inputs: arr({
        bsonType: 'object', required: ['sourceType', 'refId'],
        properties: { sourceType: en('SCORE', 'STUDENT_RESULT'), refId: OID, normalizedValue: NUM_N, weight: NUM_N }
      }),
      ruleId: OID_N, programVersionId: OID_N, syllabusId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, sectionId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1, level: 1, subjectId: 1, termId: 1 }]
    ]
  },

  aggregate_results: {
    validator: schema(['runId', 'level', 'subjectId', 'scope', 'counts'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID,
      scope: {                               // refId = lớp / học phần / CTĐT tùy type; cohort, termId bổ sung cho phạm vi khóa/học kỳ
        bsonType: 'object', required: ['type', 'refId'],
        properties: { type: en('SECTION', 'COURSE', 'COHORT', 'PROGRAM'), refId: OID, cohort: STR_N, termId: OID_N }
      },
      counts: {
        bsonType: 'object', required: ['enrolled', 'evaluable', 'passed', 'failed', 'insufficientData'],
        properties: { enrolled: INT, evaluable: INT, passed: INT, failed: INT, insufficientData: INT }
      },
      passRate: rate01, passRateOfEnrolled: rate01, targetPassRate: rate01,
      targetMet: { bsonType: ['bool', 'null'] }, dataCompleteness: rate01,
      ruleId: OID_N, programVersionId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, 'scope.type': 1, 'scope.refId': 1, 'scope.cohort': 1, 'scope.termId': 1 },
       { unique: true, name: 'uniq_aggregate_scope' }],
      [{ level: 1, subjectId: 1, 'scope.termId': 1 }]
    ]
  },

  // ----- Báo cáo, cải tiến, hỗ trợ -----
  reports: {
    validator: schema(['type', 'runId'], {
      type: en('SECTION_CLO', 'COURSE_PI', 'PROGRAM_PLO', 'DATA_COMPLETENESS'),
      title: STR_N, params: OBJ_N, runId: OID, ruleId: OID_N, programVersionId: OID_N,
      summary: OBJ_N, limitations: STR_N,
      exports: arr({
        bsonType: 'object',
        properties: { format: STR, fileRef: STR_N, generatedAt: DATE_N }
      }),
      generatedBy: OID_N, createdAt: DATE_N
    }),
    indexes: [[{ runId: 1 }], [{ type: 1, programVersionId: 1, createdAt: -1 }]]
  },

  improvement_actions: {
    validator: schema(['programVersionId', 'issue', 'ownerId', 'status'], {
      programVersionId: OID, courseId: OID_N,
      issue: {
        bsonType: 'object', required: ['level', 'subjectId'],
        properties: {
          level: en('CLO', 'PI', 'PLO'), subjectId: OID,
          reportId: OID_N, aggregateResultId: OID_N,
          observedPassRate: rate01, description: STR_N, rootCause: STR_N
        }
      },
      actionPlan: STR_N, expectedTarget: rate01,
      ownerId: OID, targetTermId: OID_N, dueDate: DATE_N,
      priority: { enum: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL', null] },
      status: en('OPEN', 'IN_PROGRESS', 'DONE', 'VERIFIED', 'CANCELLED'),
      // Đánh giá hiệu quả ở chu kỳ đo lường kế tiếp (đóng vòng cải tiến)
      evaluation: {
        bsonType: ['object', 'null'],
        properties: {
          evaluationRunId: OID_N, newPassRate: rate01, isEffective: { bsonType: ['bool', 'null'] },
          evaluatedBy: OID_N, evaluatedAt: DATE_N, completionNote: STR_N
        }
      },
      history: arr({
        bsonType: 'object', required: ['status', 'changedAt'],
        properties: { status: STR, changedBy: OID_N, changedAt: DATE, note: STR_N }
      })
    }),
    indexes: [[{ programVersionId: 1, status: 1 }], [{ ownerId: 1 }], [{ targetTermId: 1 }]]
  },

  audit_logs: {
    validator: schema(['at', 'action', 'entity'], {
      at: DATE, userId: OID_N,
      action: en('SCORE_UPDATE', 'IMPORT_CONFIRM', 'ASSESSMENT_LOCK',
                 'RUN_CALCULATION', 'RULE_CONFIRM', 'LOGIN'),
      entity: OBJ, before: OBJ_N, after: OBJ_N, reason: STR_N, ip: STR_N
    }),
    indexes: [[{ 'entity.collection': 1, 'entity.id': 1, at: -1 }], [{ userId: 1, at: -1 }]]
  }
};

// ----------------------------------------------------------------------------
// 3. Tạo collection + validator + index (chạy lại được)
// ----------------------------------------------------------------------------
const existing = new Set(d.getCollectionNames());
let created = 0, updated = 0, indexCount = 0;

Object.keys(COLLECTIONS).forEach(function (name) {
  const def = COLLECTIONS[name];
  if (!existing.has(name)) {
    d.createCollection(name, { validator: def.validator, validationLevel: 'strict', validationAction: 'error' });
    created++;
  } else {
    d.runCommand({ collMod: name, validator: def.validator, validationLevel: 'strict', validationAction: 'error' });
    updated++;
  }
  def.indexes.forEach(function (ix) {
    d.getCollection(name).createIndex(ix[0], ix[1] || {});
    indexCount++;
  });
});
print('>>> Collection: tạo mới ' + created + ', cập nhật validator ' + updated + ' · index: ' + indexCount);

// ----------------------------------------------------------------------------
// 4. Dữ liệu khởi tạo (chỉ chèn nếu chưa có; không ghi đè)
// ----------------------------------------------------------------------------
function seed(collName, filter, doc) {
  const full = Object.assign({}, filter, doc, { createdAt: NOW, updatedAt: NOW });
  d.getCollection(collName).updateOne(filter, { $setOnInsert: full }, { upsert: true });
  return d.getCollection(collName).findOne(filter)._id;
}

// 4.2 CTĐT và phiên bản
const programId = seed('programs', { code: 'CNTT' }, {
  name: 'Công nghệ thông tin', level: null, degree: null,
  extra: { note: 'Mã nội bộ của dự án; trình độ/văn bằng sẽ điền sau khi đọc thông tin chung của bản CTĐT.' }
});
const pvId = seed('program_versions', { programId: programId, version: 'BM7A-2.7-Updated-2026' }, {
  applicableCohorts: ['K23', 'K24', 'K25', 'K26', 'K27'], totalCredits: null, durationYears: null, status: 'DRAFT',
  verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null,
                  note: 'Q01: áp dụng cho các khóa K23, K24, K25, K26, K27 (nhóm SV xác nhận 2026-11-06).' }
});

// 4.3 Bảy PLO và 17 PI (Misa.md, mục 2–3; mục 7.3 trang 7–8 của bản CTĐT)
// displayOrder theo thứ tự trình bày trong tài liệu: PLO1, PLO6, PLO7, PLO4, PLO5, PLO2, PLO3
const PLO_NOTE = 'Nội dung là diễn giải ngắn theo tài liệu hướng dẫn; cần đối chiếu nguyên văn bản CTĐT ở bước trích xuất (ngày 3).';
const PLOS = [
  // [mã, thứ tự trình bày, mức năng lực ghi trong tài liệu, diễn giải ngắn]
  ['PLO1', 1, 3, 'Vận dụng toán học, thuật toán và khoa học để xác định giải pháp CNTT'],
  ['PLO2', 6, 4, 'Nhận biết trách nhiệm nghề nghiệp; xem xét giải pháp trên cơ sở pháp lý và đạo đức'],
  ['PLO3', 7, 3, 'Tiếp thu, vận dụng kiến thức mới và sử dụng chiến lược học tập phù hợp'],
  ['PLO4', 4, 3, 'Làm việc nhóm hiệu quả, tổ chức công việc và đóng góp vào mục tiêu chung'],
  ['PLO5', 5, 4, 'Giao tiếp với nhiều đối tượng và sử dụng tiếng Anh'],
  ['PLO6', 2, 6, 'Thiết kế, cài đặt và đánh giá giải pháp đáp ứng yêu cầu CNTT'],
  ['PLO7', 3, 6, 'Sử dụng công nghệ phù hợp để tạo ra giải pháp CNTT']
];
const ploId = {};
PLOS.forEach(function (p) {
  ploId[p[0]] = seed('plos', { programVersionId: pvId, code: p[0] }, {
    displayOrder: p[1], category: 'UNSPECIFIED', contentOriginal: null, contentSummary: p[3], capabilityLevelRaw: p[2],
    verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null, note: PLO_NOTE },
    reviewNote: null
  });
});

const PIS = [
  // [mã PLO, mã PI, thứ tự, mức năng lực ghi trong tài liệu, nội dung rút gọn, ghi chú cần làm rõ]
  ['PLO1', 'PI1.1', 1, 2, 'Xác định vấn đề tính toán trong CNTT', null],
  ['PLO1', 'PI1.2', 2, 3, 'Áp dụng toán học, thuật toán và khoa học để xác định giải pháp', null],
  ['PLO2', 'PI2.1', 3, 2, 'Nhận thức trách nhiệm nghề nghiệp và đạo đức', null],
  ['PLO2', 'PI2.2', 4, 4, 'Đánh giá tác động của giải pháp kỹ thuật đến cộng đồng, xã hội',
    'PI2.2 dùng động từ "Đánh giá" nhưng ghi mức 4; cần hướng dẫn thang đo (Q03).'],
  ['PLO3', 'PI3.1', 5, 3, 'Áp dụng kiến thức mới để giải quyết vấn đề CNTT', null],
  ['PLO3', 'PI3.2', 6, 3, 'Áp dụng chiến lược học tập hiệu quả', null],
  ['PLO4', 'PI4.1', 7, 3, 'Thiết lập môi trường làm việc nhóm hiệu quả', null],
  ['PLO4', 'PI4.2', 8, 3, 'Vận dụng phương pháp làm việc nhóm', null],
  ['PLO4', 'PI4.3', 9, 3, 'Thể hiện trách nhiệm và đóng góp vào thành công của nhóm', null],
  ['PLO5', 'PI5.1', 10, 3, 'Truyền đạt nội dung công việc bằng văn bản', null],
  ['PLO5', 'PI5.2', 11, 4, 'Truyền đạt hiểu biết bằng lời nói', null],
  ['PLO5', 'PI5.3', 12, 4, 'Đọc, hiểu, viết bằng tiếng Anh trong CNTT', null],
  ['PLO6', 'PI6.1', 13, 4, 'Thiết lập yêu cầu, xác định chức năng', null],
  ['PLO6', 'PI6.2', 14, 6, 'Phân tích, thiết kế và cài đặt bài toán', null],
  ['PLO6', 'PI6.3', 15, 5, 'Đánh giá giải pháp theo yêu cầu bài toán', null],
  ['PLO7', 'PI7.1', 16, 6, 'Áp dụng công nghệ phần mềm, HTTT, AI để tạo giải pháp',
    'PI7.1 mở đầu bằng "Áp dụng" nhưng ghi mức 6; cần hướng dẫn thang đo (Q03).'],
  ['PLO7', 'PI7.2', 17, 6, 'Áp dụng công nghệ mạng máy tính để tạo giải pháp',
    'PI7.2 mở đầu bằng "Áp dụng" nhưng ghi mức 6; cần hướng dẫn thang đo (Q03).']
];
const piId = {};
PIS.forEach(function (p) {
  piId[p[1]] = seed('pis', { programVersionId: pvId, code: p[1] }, {
    ploId: ploId[p[0]], displayOrder: p[2], nameOriginalTerm: 'Performance index',
    contentOriginal: null, contentShort: p[4], capabilityLevelRaw: p[3], targetPassRate: null,
    verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null,
                    note: p[5] ? (p[5] + ' ' + PLO_NOTE) : PLO_NOTE },
    reviewNote: p[5]
  });
});

// 4.4 Học phần OOP và dòng ma trận ở trang 46 (Phụ lục II)
const oopId = seed('courses', { code: 'OOPR230279' }, { name: 'Lập trình hướng đối tượng', credits: null });
[['PI1.2', 'R'], ['PI6.1', 'R'], ['PI6.2', 'I'], ['PI7.1', 'R']].forEach(function (r) {
  seed('course_pi_contributions', { programVersionId: pvId, courseId: oopId, piId: piId[r[0]] }, {
    contribution: r[1],
    verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null,
                    note: 'Chép theo ví dụ trong tài liệu hướng dẫn; cần đối chiếu lại trang 46 của bản CTĐT.' }
  });
});

// 4.5 Quy tắc đo lường: khung bản nháp, chưa có công thức/ngưỡng chính thức
seed('measurement_rules', { code: 'QT-PDBCL-DLCDR', version: '0.1-DRAFT' }, {
  status: 'DRAFT', confirmedBy: null, confirmedAt: null,
  scope: { programVersionId: pvId, courseIds: null },
  clo: { method: null, defaultPassThreshold: null, minCoverage: null },
  course: { requireAllCloPass: null, defaultTargetPassRate: null },
  // BLANK luôn tính 0 (có ghi nhận); cách xử lý ABSENT/MISSING trong mẫu số chờ Khoa (Q13)
  missingDataPolicy: { blankScoreValue: 0, excludeAbsentFromDenominator: null, excludeMissingFromDenominator: null },
  // ADR 0011 (Q10): PI suy ra từ CLO; trọng số CLO → PI (clo_pi_links.weight) chờ Q22. ADR 0012 (Q18): không quy đổi I/R/M
  pi: { method: 'FROM_CLOS', aggregationMethod: null, defaultPassThreshold: null },
  plo: { method: null, defaultPassThreshold: null, cohortTargetPassRate: null },
  notes: 'BẢN NHÁP: các ngưỡng, trọng số, mẫu số để trống chờ Khoa xác nhận (Q04, Q12, Q13). Không dùng để kết luận chính thức.'
});

// 4.7 (Tùy chọn) Dữ liệu MINH HỌA: CLO ví dụ của học phần OOP (Misa.md, mục 4)
if (LOAD_ILLUSTRATIVE_SAMPLE) {
  const syl = seed('syllabi', { courseId: oopId, version: 'ILLUSTRATIVE-DRAFT' }, {
    effectiveFromTermId: null, effectiveToTermId: null, status: 'DRAFT',
    verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                    note: 'ĐỀ CƯƠNG MINH HỌA, không phải đề cương chính thức. Thay bằng đề cương thật khi Khoa cung cấp (Q02).' }
  });
  [
    ['CLO1', 'Giải thích đóng gói, kế thừa và đa hình bằng ví dụ lập trình', 2],
    ['CLO2', 'Phân tích yêu cầu một bài toán quản lý để xác định lớp, thuộc tính, phương thức và quan hệ', 4],
    ['CLO3', 'Xây dựng chương trình hướng đối tượng đáp ứng các chức năng được giao', 3],
    ['CLO4', 'Đánh giá chương trình theo tiêu chí tính đúng đắn, cấu trúc thiết kế và khả năng bảo trì', 5]
  ].forEach(function (c) {
    seed('clos', { syllabusId: syl, code: c[0] }, {
      courseId: oopId, contentOriginal: c[1], bloomLevel: c[2], bloomNote: 'Mức Bloom do người soạn đề xuất (minh họa).',
      measurement: null,
      verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                      note: 'CLO MINH HỌA do người hướng dẫn đề xuất, không phải CLO chính thức.' }
    });
  });
  print('>>> Đã nạp dữ liệu MINH HỌA (đề cương + 4 CLO của OOP');
}

// ----------------------------------------------------------------------------
// 5. Tóm tắt
// ----------------------------------------------------------------------------
print('>>> Số document khởi tạo:');
['programs', 'program_versions', 'plos', 'pis', 'courses',
 'course_pi_contributions', 'measurement_rules', 'syllabi', 'clos'].forEach(function (n) {
  print('      ' + n + ': ' + d.getCollection(n).countDocuments({}));
});
print('>>> HOÀN TẤT. Tổng số collection: ' + d.getCollectionNames().length);
