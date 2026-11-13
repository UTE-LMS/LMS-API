/*
 * ============================================================================
 *  KHỞI TẠO CƠ SỞ DỮ LIỆU MONGODB
 *  Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra CTĐT
 *  (Learning Outcomes Management and Analytics System)
 *  Thiết kế: GiaiThich_CoSoDuLieu.md v0.7 (25 collections — cập nhật theo dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md)
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
 *   1. Tạo 25 collection kèm quy tắc kiểm tra cấu trúc ($jsonSchema) và index.
 *   2. Nạp dữ liệu khởi tạo: 7 PLO, 17 PI (kèm trọng số đóng góp và ngưỡng đạt mẫu) và ma trận I/R/M
 *      của học phần OOPR230279.
 *   Lưu ý:
 *   - Đã loại bỏ 3 collection ngân hàng câu hỏi (question_banks, question_categories, questions)
 *     và 1 collection quy tắc rời rạc (measurement_rules) -> còn 25 collections.
 *   - Ngưỡng đạt (passThreshold) và trọng số đóng góp được lưu bắt buộc trực tiếp trên plos, pis,
 *     clos, clo_pi_links và assessments.cloWeights.
 *   - Bảng điểm (scores) lưu trực tiếp rawScore >= 0 theo từng cặp (itemId, cloId) (vắng thi / bỏ trống = 0.0,
 *     bỏ các trạng thái điểm rỗng SCORE_STATUS_VALUES).
 *   - Kết quả đo lường lưu xuyên suốt 4 cấp độ: RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO'].
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
const OID      = { bsonType: 'objectId' };
const OID_N    = { bsonType: ['objectId', 'null'] };
const STR      = { bsonType: 'string' };
const STR_N    = { bsonType: ['string', 'null'] };
const BOOL     = { bsonType: 'bool' };
const DATE     = { bsonType: 'date' };
const DATE_N   = { bsonType: ['date', 'null'] };
const NUM      = { bsonType: ['double', 'int', 'long', 'decimal'] };
const NUM_N    = { bsonType: ['double', 'int', 'long', 'decimal', 'null'] };
const INT      = { bsonType: ['int', 'long', 'double'] };
const INT_N    = { bsonType: ['int', 'long', 'double', 'null'] };
const OBJ      = { bsonType: 'object' };
const OBJ_N    = { bsonType: ['object', 'null'] };
const arr      = (items) => ({ bsonType: 'array', items });
const en       = (...v) => ({ enum: v });
const bloom    = { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 6 };
// Trọng số và ngưỡng đạt bắt buộc thuộc khoảng (0, 1] (không được để trống)
const weight01 = { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0, exclusiveMinimum: true, maximum: 1 };
const rate01   = { bsonType: ['double', 'int', 'long', 'decimal', 'null'], minimum: 0, maximum: 1 };
const level16  = { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 6 };

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
// Mỗi câu hỏi ánh xạ tới 1 hoặc nhiều CLO; giảng viên khai báo trực tiếp điểm tối đa (maxScore > 0)
const OUTCOME_MAPPING = {
  bsonType: 'object', required: ['cloId', 'maxScore'],
  properties: { cloId: OID, maxScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0, exclusiveMinimum: true } }
};
// Tỷ lệ đóng góp W_{p,j} thuộc (0, 1] của bài kiểm tra cho từng CLO có mặt trong bài
const ASSESSMENT_CLO_WEIGHT = {
  bsonType: 'object', required: ['cloId', 'weight'],
  properties: { cloId: OID, weight: weight01 }
};
// Biểu thức: trong một danh sách, mỗi cloId chỉ xuất hiện một lần
const noDuplicateClos = (mappingsPath) => ({ $eq: [ { $size: mappingsPath },
  { $size: { $setUnion: [ { $map: { input: mappingsPath, as: 'm', in: '$$m.cloId' } }, [] ] } } ] });

// 4 cấp độ kết quả chuẩn đầu ra (bỏ COURSE, thêm ASSESSMENT_CLO cho Cấp độ 1)
const RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO'];
const ASSESSMENT_LOCKED_STATUSES = ['LOCKED', 'SCORE_ENTERED'];
const ASSESSMENT_NEED_ITEMS_STATUSES = ['LOCKED', 'SCORE_ENTERED'];

const COLLECTIONS = {

  // ----- Người dùng -----
  users: {
    validator: schema(['username', 'email', 'passwordHash', 'status', 'roleAssignments'], {
      username: STR, email: STR, passwordHash: STR, fullName: STR_N,
      status: en('ACTIVE', 'LOCKED'), tokenVersion: INT,
      roleAssignments: arr({
        bsonType: 'object', required: ['role'],
        properties: {
          role: en('ADMIN', 'PROGRAM_MANAGER', 'LECTURER', 'QA'),
          scope: {
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
      totalCredits: INT_N,
      durationYears: NUM_N,
      status: en('DRAFT', 'ACTIVE', 'ARCHIVED'),
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ programId: 1, version: 1 }, { unique: true }]]
  },

  plos: {
    validator: schema(['programVersionId', 'code', 'displayOrder', 'passThreshold', 'verification'], {
      programVersionId: OID,
      code: { bsonType: 'string', pattern: '^PLO[1-9][0-9]*$' },
      displayOrder: INT,
      category: { enum: ['GENERAL', 'SPECIALIZED', 'UNSPECIFIED', null] },
      contentOriginal: STR_N,
      contentSummary: STR_N,
      capabilityLevelRaw: level16,
      passThreshold: weight01,           // Ngưỡng đạt bắt buộc của PLO thuộc (0, 1]
      verification: VERIFICATION, reviewNote: STR_N
    }),
    indexes: [[{ programVersionId: 1, code: 1 }, { unique: true }]]
  },

  pis: {
    validator: schema(['programVersionId', 'ploId', 'code', 'displayOrder', 'weight', 'passThreshold', 'verification'], {
      programVersionId: OID, ploId: OID,
      code: { bsonType: 'string', pattern: '^PI[1-9][0-9]*\\.[1-9][0-9]*$' },
      displayOrder: INT,
      nameOriginalTerm: STR_N,
      contentOriginal: STR_N, contentShort: STR_N,
      capabilityLevelRaw: level16,
      weight: weight01,                  // Trọng số đóng góp W_{PI -> PLO} thuộc (0, 1] (tổng các PI trong cùng PLO = 1.0)
      passThreshold: weight01,           // Ngưỡng đạt bắt buộc của PI thuộc (0, 1]
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
    validator: schema(['syllabusId', 'courseId', 'code', 'contentOriginal', 'measurement'], {
      syllabusId: OID, courseId: OID,
      code: { bsonType: 'string', pattern: '^CLO[0-9]+$' },
      contentOriginal: STR, bloomLevel: bloom, bloomNote: STR_N,
      measurement: {
        bsonType: 'object', required: ['defaultPassThreshold'],
        properties: { defaultPassThreshold: weight01 } // Ngưỡng đạt bắt buộc của CLO thuộc (0, 1] (đã bỏ defaultWeight)
      },
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ syllabusId: 1, code: 1 }, { unique: true }]]
  },

  clo_pi_links: {
    validator: schema(['programVersionId', 'syllabusId', 'cloId', 'piId', 'weight'], {
      programVersionId: OID, syllabusId: OID, cloId: OID, piId: OID,
      // Trọng số đóng góp W_{CLO -> PI} thuộc (0, 1] (bắt buộc không để trống; không ép tổng qua các học phần = 1.0)
      weight: weight01,
      verification: VERIFICATION
    }),
    indexes: [[{ cloId: 1, piId: 1 }, { unique: true }], [{ piId: 1 }], [{ programVersionId: 1 }]]
  },

  // ----- Bài đánh giá & Ma trận Câu hỏi – CLO -----
  assessments: {
    validator: {
      $and: [
        schema(['syllabusId', 'code', 'name', 'scope', 'status', 'isLocked'], {
          syllabusId: OID, code: STR, name: STR,
          scope: en('SYLLABUS_TEMPLATE', 'SECTION'),
          sectionId: OID_N, templateId: OID_N,
          type: { enum: ['QUIZ', 'PRACTICAL', 'PROJECT', 'MIDTERM', 'FINAL', 'OTHER', null] },
          phase: { enum: ['FORMATIVE', 'MIDTERM', 'SUMMATIVE', null] },
          scheduledAt: DATE_N,
          status: en('DRAFT', 'LOCKED', 'SCORE_ENTERED'),
          isLocked: BOOL,
          // Tỷ lệ đóng góp W_{p,j} thuộc (0, 1] của bài kiểm tra cho từng CLO có mặt trong bài
          cloWeights: arr(ASSESSMENT_CLO_WEIGHT),
          coverageSummary: {
            bsonType: ['object', 'null'],
            properties: {
              totalItemsSum: NUM_N,
              byClo: arr({ bsonType: 'object', required: ['cloId'], properties: { cloId: OID, points: NUM_N, percentage: NUM_N } }),
              byBloom: arr({ bsonType: 'object', required: ['bloomLevel'],
                             properties: { bloomLevel: bloom, questionCount: INT_N, points: NUM_N, percentage: NUM_N } })
            }
          },
          items: arr({
            bsonType: 'object', required: ['_id', 'code', 'outcomeMappings'],
            properties: {
              _id: OID, code: STR, orderIndex: INT_N,
              bloomLevel: bloom,
              outcomeMappings: { bsonType: 'array', minItems: 1, items: OUTCOME_MAPPING }
            }
          }),
          source: OBJ_N, verification: VERIFICATION, extra: OBJ_N
        }),
        { $expr: { $or: [ { $ne: ['$scope', 'SECTION'] }, { $eq: [{ $type: '$sectionId' }, 'objectId'] } ] } },
        { $expr: { $eq: ['$isLocked', { $in: ['$status', ASSESSMENT_LOCKED_STATUSES] }] } },
        { $expr: { $or: [ { $not: [{ $in: ['$status', ASSESSMENT_NEED_ITEMS_STATUSES] }] },
                          { $gte: [{ $size: { $ifNull: ['$items', []] } }, 1] } ] } },
        { $expr: { $allElementsTrue: [ { $map: { input: { $ifNull: ['$items', []] }, as: 'it',
            in: noDuplicateClos('$$it.outcomeMappings') } } ] } },
        { $expr: noDuplicateClos({ $ifNull: ['$cloWeights', []] }) }
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
      userId: OID_N,
      cohort: STR_N, majorCode: STR_N, programVersionId: OID_N,
      fullName: STR_N, email: STR_N,
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
    validator: schema(['sectionId', 'assessmentId', 'status'], {
      sectionId: OID, assessmentId: OID,
      fileName: STR_N, fileChecksum: STR_N, fileRef: STR_N,
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
        schema(['sectionId', 'assessmentId', 'itemId', 'cloId', 'studentId', 'rawScore', 'maxScore'], {
          sectionId: OID, assessmentId: OID, itemId: OID, cloId: OID, studentId: OID,
          // Vắng thi hoặc bỏ trống câu hỏi lưu trực tiếp rawScore = 0.0 (đã bỏ status và rubricLevel)
          rawScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0 },
          maxScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0, exclusiveMinimum: true },
          importBatchId: OID_N, sourceRow: INT_N,
          updatedBy: OID_N, updatedAt: DATE_N
        }),
        { $expr: { $lte: ['$rawScore', '$maxScore'] } }
      ]
    },
    indexes: [
      [{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1 }],
      [{ importBatchId: 1 }]
    ]
  },

  // ----- Đo lường 4 cấp độ (ASSESSMENT_CLO -> CLO -> PI -> PLO) -----
  calculation_runs: {
    validator: schema(['scope', 'status', 'lockKey'], {
      scope: en('SECTION', 'COHORT', 'PROGRAM'),
      scopeRef: {
        bsonType: ['object', 'null'],
        properties: { sectionIds: arr(OID), courseIds: arr(OID), cohorts: arr(STR), termId: OID_N }
      },
      lockKey: STR, programVersionId: OID_N, syllabusIds: arr(OID),
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
      [{ scope: 1, startedAt: -1 }],
      [{ 'scopeRef.sectionIds': 1, startedAt: -1 }],
      [{ lockKey: 1 }, { unique: true, partialFilterExpression: { status: { $in: ['PENDING', 'PROCESSING'] } }, name: 'uniq_running_lockKey' }]
    ]
  },

  student_results: {
    validator: schema(['runId', 'level', 'subjectId', 'studentId', 'status'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID,
      // assessmentId khác null khi level = 'ASSESSMENT_CLO'; bằng null khi level thuộc 'CLO' | 'PI' | 'PLO'
      sectionId: OID_N, assessmentId: OID_N, studentId: OID, termId: OID_N,
      status: en('COMPUTED', 'INSUFFICIENT_DATA', 'NOT_APPLICABLE'),
      value: rate01, passThreshold: rate01, passed: { bsonType: ['bool', 'null'] },
      completeness: {
        bsonType: ['object', 'null'],
        properties: { requiredCount: INT_N, availableCount: INT_N, ratio: rate01 }
      },
      inputs: arr({
        bsonType: 'object', required: ['sourceType', 'refId'],
        properties: { sourceType: en('SCORE', 'STUDENT_RESULT'), refId: OID, normalizedValue: NUM_N, weight: NUM_N }
      }),
      programVersionId: OID_N, syllabusId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1, level: 1, subjectId: 1, termId: 1 }]
    ]
  },

  aggregate_results: {
    validator: schema(['runId', 'level', 'subjectId', 'scope', 'counts'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID,
      sectionId: OID_N, assessmentId: OID_N,
      scope: {
        bsonType: 'object', required: ['type', 'refId'],
        properties: { type: en('ASSESSMENT', 'SECTION', 'COHORT', 'PROGRAM'), refId: OID, cohort: STR_N, termId: OID_N }
      },
      counts: {
        bsonType: 'object', required: ['enrolled', 'passed', 'failed'],
        properties: { enrolled: INT, passed: INT, failed: INT }
      },
      mean: rate01, passRate: rate01, passThreshold: rate01,
      dataCompleteness: rate01,
      programVersionId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1, 'scope.type': 1, 'scope.refId': 1, 'scope.cohort': 1, 'scope.termId': 1 },
       { unique: true, name: 'uniq_aggregate_scope' }],
      [{ level: 1, subjectId: 1, 'scope.termId': 1 }]
    ]
  },

  // ----- Báo cáo, cải tiến, hỗ trợ -----
  reports: {
    validator: schema(['type', 'runId'], {
      type: en('SECTION_CLO', 'COURSE_PI', 'PROGRAM_PLO', 'DATA_COMPLETENESS'),
      title: STR_N, params: OBJ_N, runId: OID, programVersionId: OID_N,
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
      action: en('SCORE_UPDATE', 'IMPORT_CONFIRM', 'ASSESSMENT_LOCK', 'ASSESSMENT_UNLOCK',
                 'RUN_CALCULATION', 'LOGIN'),
      entity: OBJ, before: OBJ_N, after: OBJ_N, reason: STR_N, ip: STR_N
    }),
    indexes: [[{ 'entity.collection': 1, 'entity.id': 1, at: -1 }], [{ userId: 1, at: -1 }]]
  }
};

// ----------------------------------------------------------------------------
// 3. Tạo collection + validator + index (chạy lại được)
// ----------------------------------------------------------------------------
const existing = new Set(d.getCollectionNames());
// Dọn dẹp 4 collection đã loại bỏ nếu tồn tại rỗng từ phiên bản trước
['question_banks', 'question_categories', 'questions', 'measurement_rules'].forEach(function (obs) {
  if (existing.has(obs) && d.getCollection(obs).countDocuments({}) === 0) {
    d.getCollection(obs).drop();
    existing.delete(obs);
  }
});
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

// 4.1 CTĐT và phiên bản
const programId = seed('programs', { code: 'CNTT' }, {
  name: 'Công nghệ thông tin', level: null, degree: null,
  extra: { note: 'Mã nội bộ của dự án; trình độ/văn bằng sẽ điền sau khi đọc thông tin chung của bản CTĐT.' }
});
const pvId = seed('program_versions', { programId: programId, version: 'BM7A-2.7-Updated-2026' }, {
  applicableCohorts: ['K23', 'K24', 'K25', 'K26', 'K27'], totalCredits: null, durationYears: null, status: 'DRAFT',
  verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null,
                  note: 'Q01: áp dụng cho các khóa K23, K24, K25, K26, K27 (nhóm SV xác nhận 2026-11-06).' }
});

// 4.2 Bảy PLO và 17 PI (kèm ngưỡng đạt 0.60 và trọng số PI -> PLO tổng = 1.0 cho mỗi PLO)
const PLO_NOTE = 'Nội dung là diễn giải ngắn theo tài liệu hướng dẫn; cập nhật qua Import CSV CTĐT.';
const PLOS = [
  // [mã, thứ tự trình bày, mức năng lực, ngưỡng đạt passThreshold, diễn giải ngắn]
  ['PLO1', 1, 3, 0.60, 'Vận dụng toán học, thuật toán và khoa học để xác định giải pháp CNTT'],
  ['PLO2', 6, 4, 0.60, 'Nhận biết trách nhiệm nghề nghiệp; xem xét giải pháp trên cơ sở pháp lý và đạo đức'],
  ['PLO3', 7, 3, 0.60, 'Tiếp thu, vận dụng kiến thức mới và sử dụng chiến lược học tập phù hợp'],
  ['PLO4', 4, 3, 0.60, 'Làm việc nhóm hiệu quả, tổ chức công việc và đóng góp vào mục tiêu chung'],
  ['PLO5', 5, 4, 0.60, 'Giao tiếp với nhiều đối tượng và sử dụng tiếng Anh'],
  ['PLO6', 2, 6, 0.60, 'Thiết kế, cài đặt và đánh giá giải pháp đáp ứng yêu cầu CNTT'],
  ['PLO7', 3, 6, 0.60, 'Sử dụng công nghệ phù hợp để tạo ra giải pháp CNTT']
];
const ploId = {};
PLOS.forEach(function (p) {
  ploId[p[0]] = seed('plos', { programVersionId: pvId, code: p[0] }, {
    displayOrder: p[1], category: 'UNSPECIFIED', contentOriginal: null, contentSummary: p[4],
    capabilityLevelRaw: p[2], passThreshold: p[3],
    verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null, note: PLO_NOTE },
    reviewNote: null
  });
});

const PIS = [
  // [mã PLO, mã PI, thứ tự, mức năng lực, trọng số W_{PI -> PLO}, ngưỡng đạt passThreshold, nội dung rút gọn, ghi chú]
  ['PLO1', 'PI1.1', 1, 2, 0.40, 0.60, 'Xác định vấn đề tính toán trong CNTT', null],
  ['PLO1', 'PI1.2', 2, 3, 0.60, 0.60, 'Áp dụng toán học, thuật toán và khoa học để xác định giải pháp', null],
  ['PLO2', 'PI2.1', 3, 2, 0.50, 0.60, 'Nhận thức trách nhiệm nghề nghiệp và đạo đức', null],
  ['PLO2', 'PI2.2', 4, 4, 0.50, 0.60, 'Đánh giá tác động của giải pháp kỹ thuật đến cộng đồng, xã hội', null],
  ['PLO3', 'PI3.1', 5, 3, 0.50, 0.60, 'Áp dụng kiến thức mới để giải quyết vấn đề CNTT', null],
  ['PLO3', 'PI3.2', 6, 3, 0.50, 0.60, 'Áp dụng chiến lược học tập hiệu quả', null],
  ['PLO4', 'PI4.1', 7, 3, 0.30, 0.60, 'Thiết lập môi trường làm việc nhóm hiệu quả', null],
  ['PLO4', 'PI4.2', 8, 3, 0.30, 0.60, 'Vận dụng phương pháp làm việc nhóm', null],
  ['PLO4', 'PI4.3', 9, 3, 0.40, 0.60, 'Thể hiện trách nhiệm và đóng góp vào thành công của nhóm', null],
  ['PLO5', 'PI5.1', 10, 3, 0.30, 0.60, 'Truyền đạt nội dung công việc bằng văn bản', null],
  ['PLO5', 'PI5.2', 11, 4, 0.30, 0.60, 'Truyền đạt hiểu biết bằng lời nói', null],
  ['PLO5', 'PI5.3', 12, 4, 0.40, 0.60, 'Đọc, hiểu, viết bằng tiếng Anh trong CNTT', null],
  ['PLO6', 'PI6.1', 13, 4, 0.30, 0.60, 'Thiết lập yêu cầu, xác định chức năng', null],
  ['PLO6', 'PI6.2', 14, 6, 0.40, 0.60, 'Phân tích, thiết kế và cài đặt bài toán', null],
  ['PLO6', 'PI6.3', 15, 5, 0.30, 0.60, 'Đánh giá giải pháp theo yêu cầu bài toán', null],
  ['PLO7', 'PI7.1', 16, 6, 0.50, 0.60, 'Áp dụng công nghệ phần mềm, HTTT, AI để tạo giải pháp', null],
  ['PLO7', 'PI7.2', 17, 6, 0.50, 0.60, 'Áp dụng công nghệ mạng máy tính để tạo giải pháp', null]
];
const piId = {};
PIS.forEach(function (p) {
  piId[p[1]] = seed('pis', { programVersionId: pvId, code: p[1] }, {
    ploId: ploId[p[0]], displayOrder: p[2], nameOriginalTerm: 'Performance index',
    contentOriginal: null, contentShort: p[6], capabilityLevelRaw: p[3],
    weight: p[4], passThreshold: p[5],
    verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                    note: p[7] ? (p[7] + ' ' + PLO_NOTE) : PLO_NOTE },
    reviewNote: p[7]
  });
});

// 4.3 Học phần OOP và dòng ma trận ở trang 46 (Phụ lục II)
const oopId = seed('courses', { code: 'OOPR230279' }, { name: 'Lập trình hướng đối tượng', credits: null });
[['PI1.2', 'R'], ['PI6.1', 'R'], ['PI6.2', 'I'], ['PI7.1', 'R']].forEach(function (r) {
  seed('course_pi_contributions', { programVersionId: pvId, courseId: oopId, piId: piId[r[0]] }, {
    contribution: r[1],
    verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                    note: 'Chép theo ví dụ trong tài liệu hướng dẫn; đối chiếu bản CTĐT.' }
  });
});

// 4.4 (Tùy chọn) Dữ liệu MINH HỌA: CLO ví dụ của học phần OOP
if (LOAD_ILLUSTRATIVE_SAMPLE) {
  const syl = seed('syllabi', { courseId: oopId, version: 'ILLUSTRATIVE-DRAFT' }, {
    effectiveFromTermId: null, effectiveToTermId: null, status: 'DRAFT',
    verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                    note: 'ĐỀ CƯƠNG MINH HỌA, không phải đề cương chính thức.' }
  });
  [
    ['CLO1', 'Giải thích đóng gói, kế thừa và đa hình bằng ví dụ lập trình', 2],
    ['CLO2', 'Phân tích yêu cầu một bài toán quản lý để xác định lớp, thuộc tính, phương thức và quan hệ', 4],
    ['CLO3', 'Xây dựng chương trình hướng đối tượng đáp ứng các chức năng được giao', 3],
    ['CLO4', 'Đánh giá chương trình theo tiêu chí tính đúng đắn, cấu trúc thiết kế và khả năng bảo trì', 5]
  ].forEach(function (c) {
    seed('clos', { syllabusId: syl, code: c[0] }, {
      courseId: oopId, contentOriginal: c[1], bloomLevel: c[2], bloomNote: 'Mức Bloom do người soạn đề xuất (minh họa).',
      measurement: { defaultPassThreshold: 0.60 },
      verification: { status: 'UNVERIFIED', verifiedBy: null, verifiedAt: null,
                      note: 'CLO MINH HỌA do người hướng dẫn đề xuất, không phải CLO chính thức.' }
    });
  });
  print('>>> Đã nạp dữ liệu MINH HỌA (đề cương + 4 CLO của OOP)');
}

// ----------------------------------------------------------------------------
// 5. Tóm tắt
// ----------------------------------------------------------------------------
print('>>> Số document khởi tạo:');
['programs', 'program_versions', 'plos', 'pis', 'courses',
 'course_pi_contributions', 'syllabi', 'clos'].forEach(function (n) {
  print('      ' + n + ': ' + d.getCollection(n).countDocuments({}));
});
print('>>> HOÀN TẤT. Tổng số collection: ' + d.getCollectionNames().length);
