/*
 * ============================================================================
 *  KHỞI TẠO CƠ SỞ DỮ LIỆU MONGODB
 *  Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra CTĐT
 *  (Learning Outcomes Management and Analytics System)
 *  Thiết kế: MongoDB_Design.md v0.4
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
 *   1. Tạo 30 collection kèm quy tắc kiểm tra cấu trúc ($jsonSchema) và index.
 *   2. Nạp dữ liệu khởi tạo lấy từ tài liệu: 7 PLO, 17 PI, ma trận I/R/M của học phần
 *      OOPR230279 (trang 46) và khung quy tắc đo lường bản nháp.
 *   Ngân hàng câu hỏi (question_banks, question_categories, questions) được tạo rỗng:
 *   danh mục và câu hỏi chỉ nhập khi có đề đã thi và ánh xạ CLO–PI đã duyệt.
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
// Cặp (CLO, PI) mà một câu hỏi/tiêu chí đo, kèm phần điểm của câu được chia cho cặp đó (tổng các cặp của một câu = 1)
const OUTCOME_MAPPING = {
  bsonType: 'object', required: ['cloId', 'piId', 'weight'],
  properties: { cloId: OID, piId: OID, weight: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0, maximum: 1 } }
};
// Biểu thức: tổng các trọng số trong mảng bằng 1 (sai số 0,0001)
// Biểu thức: trong một câu hỏi, mỗi cặp (CLO, PI) chỉ khai báo một lần (tránh tính trùng điểm)
const noDuplicatePairs = (mappingsPath) => ({ $eq: [ { $size: mappingsPath },
  { $size: { $setUnion: [ { $map: { input: mappingsPath, as: 'm', in: { cloId: '$$m.cloId', piId: '$$m.piId' } } }, [] ] } } ] });
const sumsToOne = (weightsPath) => ({ $lte: [{ $abs: { $subtract: [{ $sum: weightsPath }, 1] } }, 0.0001] });

const SCORE_STATUS_VALUES = ['SCORED', 'BLANK', 'ABSENT', 'NOT_GRADED', 'EXEMPT', 'MISSING'];
const RESULT_LEVELS = ['CLO', 'COURSE', 'PI', 'PLO'];

const COLLECTIONS = {

  // ----- Người dùng -----
  users: {
    validator: schema(['username', 'email', 'passwordHash', 'status', 'roleAssignments'], {
      username: STR, email: STR, passwordHash: STR, fullName: STR_N,
      status: en('ACTIVE', 'LOCKED'),
      roleAssignments: arr({
        bsonType: 'object', required: ['role'],
        properties: {
          role: en('ADMIN', 'PROGRAM_MANAGER', 'QA_OFFICER', 'LECTURER', 'VIEWER'),
          scope: OBJ_N
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
      status: en('DRAFT', 'ACTIVE', 'ARCHIVED'),
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ programId: 1, version: 1 }, { unique: true }]]
  },

  plos: {
    validator: schema(['programVersionId', 'code', 'displayOrder', 'verification'], {
      programVersionId: OID,
      code: { bsonType: 'string', pattern: '^PLO[0-9]+$' },
      displayOrder: INT,
      contentOriginal: STR_N,            // nguyên văn trong CTĐT (null cho đến khi đối chiếu bản gốc)
      contentSummary: STR_N,             // diễn giải ngắn
      capabilityLevelRaw: NUM_N,         // đúng số trong cột "Trình độ năng lực", không tính từ PI
      verification: VERIFICATION, reviewNote: STR_N
    }),
    indexes: [[{ programVersionId: 1, code: 1 }, { unique: true }]]
  },

  pis: {
    validator: schema(['programVersionId', 'ploId', 'code', 'displayOrder', 'verification'], {
      programVersionId: OID, ploId: OID,
      code: { bsonType: 'string', pattern: '^PI[0-9]+\\.[0-9]+$' },
      displayOrder: INT,
      nameOriginalTerm: STR_N,
      contentOriginal: STR_N, contentShort: STR_N,
      capabilityLevelRaw: NUM_N,
      targetPassRate: NUM_N,
      verification: VERIFICATION, reviewNote: STR_N
    }),
    indexes: [[{ programVersionId: 1, code: 1 }, { unique: true }], [{ ploId: 1 }]]
  },

  courses: {
    validator: schema(['code', 'name'], { code: STR, name: STR, credits: NUM_N, extra: OBJ_N }),
    indexes: [[{ code: 1 }, { unique: true }]]
  },

  program_courses: {
    validator: schema(['programVersionId', 'courseId'], {
      programVersionId: OID, courseId: OID, semester: INT_N, credits: NUM_N,
      type: { enum: ['REQUIRED', 'ELECTIVE', null] },
      category: { enum: ['FOUNDATION', 'MAJOR_FOUNDATION', 'SPECIALIZED', 'CAPSTONE_PROJECT', 'THESIS', null] },
      prerequisiteCourseIds: arr(OID)
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
      measurement: OBJ_N,                // { weightInCourse, passThreshold, verification }
      verification: VERIFICATION, extra: OBJ_N
    }),
    indexes: [[{ syllabusId: 1, code: 1 }, { unique: true }]]
  },

  clo_pi_links: {
    validator: schema(['programVersionId', 'syllabusId', 'cloId', 'piId'], {
      programVersionId: OID, syllabusId: OID, cloId: OID, piId: OID,
      weight: NUM_N, verification: VERIFICATION
    }),
    indexes: [[{ cloId: 1, piId: 1 }, { unique: true }], [{ piId: 1 }], [{ programVersionId: 1 }]]
  },

  // ----- Ngân hàng câu hỏi (danh mục câu hỏi của đề ĐÃ THI; không sinh đề, không duyệt đề) -----
  question_banks: {
    validator: schema(['code', 'name', 'courseId', 'syllabusId', 'sourceSystem', 'status'], {
      code: STR, name: STR, description: STR_N,
      courseId: OID, syllabusId: OID,            // ngân hàng gắn với phiên bản đề cương vì CLO chỉ có nghĩa trong một đề cương
      sourceSystem: en('INTERNAL', 'MOODLE', 'EXCEL_IMPORT'),
      externalBankId: STR_N,
      status: en('ACTIVE', 'ARCHIVED'),
      extra: OBJ_N
    }),
    indexes: [[{ code: 1 }, { unique: true }], [{ courseId: 1, syllabusId: 1 }]]
  },

  // Cây danh mục nội dung của một ngân hàng: Chương/Phần (cấp 1) → Chủ đề (cấp 2) → Nhóm nhỏ (cấp 3)
  question_categories: {
    validator: {
      $and: [
        schema(['bankId', 'code', 'name', 'parentId', 'ancestorIds', 'level', 'status'], {
          bankId: OID, code: STR, name: STR, description: STR_N,
          parentId: OID_N, ancestorIds: arr(OID),
          level: { bsonType: ['int', 'long', 'double'], minimum: 1, maximum: 3 },
          order: INT_N, status: en('ACTIVE', 'ARCHIVED')
        }),
        // cấp 1 ⇔ không có cha; số tổ tiên = cấp − 1
        { $expr: { $eq: [{ $eq: ['$level', 1] }, { $eq: [{ $type: '$parentId' }, 'null'] }] } },
        { $expr: { $eq: [{ $size: '$ancestorIds' }, { $subtract: ['$level', 1] }] } }
      ]
    },
    indexes: [
      [{ bankId: 1, code: 1 }, { unique: true }],
      [{ bankId: 1, parentId: 1, order: 1 }],
      [{ ancestorIds: 1 }]
    ]
  },

  questions: {
    validator: {
      $and: [
        schema(['bankId', 'code', 'version', 'questionType', 'categoryId', 'bloomLevel', 'outcomeMappings', 'status'], {
          bankId: OID, code: STR, version: INT,
          questionType: en('MCQ', 'SHORT_ANSWER', 'ESSAY', 'PRACTICAL', 'PROJECT_TASK'),
          content: STR_N,                        // nội dung câu hỏi (có thể để trống nếu đề còn bảo mật)
          categoryId: OID,                       // mỗi câu thuộc đúng MỘT danh mục lá hoặc nhánh của cây
          bloomLevel: { bsonType: ['int', 'long', 'double'], minimum: 1, maximum: 6 },
          difficulty: { enum: ['EASY', 'MEDIUM', 'HARD', null] },
          defaultMaxScore: NUM_N,
          outcomeMappings: arr(OUTCOME_MAPPING), // một câu có thể đo nhiều cặp (CLO, PI)
          source: OBJ_N,                         // { system: INTERNAL|MOODLE|EXCEL_IMPORT, externalQuestionId, note }
          status: en('DRAFT', 'ACTIVE', 'RETIRED'),
          verification: VERIFICATION, extra: OBJ_N
        }),
        // Câu ở trạng thái ACTIVE phải có ít nhất một cặp (CLO, PI) và tổng phần điểm = 1
        { $expr: { $or: [ { $ne: ['$status', 'ACTIVE'] },
            { $and: [ { $gte: [{ $size: '$outcomeMappings' }, 1] }, sumsToOne('$outcomeMappings.weight') ] } ] } },
        // mỗi cặp (CLO, PI) chỉ xuất hiện một lần trong một câu
        { $expr: noDuplicatePairs('$outcomeMappings') }
      ]
    },
    indexes: [
      [{ bankId: 1, code: 1 }, { unique: true }],
      [{ categoryId: 1 }],
      [{ bankId: 1, status: 1, bloomLevel: 1 }],
      [{ 'outcomeMappings.cloId': 1 }],
      [{ 'outcomeMappings.piId': 1 }],
      // chống nhập trùng một câu từ hệ thống ngoài (Moodle...)
      [{ bankId: 1, 'source.system': 1, 'source.externalQuestionId': 1 },
       { unique: true, partialFilterExpression: { 'source.externalQuestionId': { $type: 'string' } }, name: 'uniq_external_question' }]
    ]
  },

  assessments: {
    validator: {
      $and: [
        schema(['syllabusId', 'code', 'name', 'scope', 'dataGranularity'], {
          syllabusId: OID, code: STR, name: STR,
          scope: en('SYLLABUS_TEMPLATE', 'SECTION'),
          sectionId: OID_N, templateId: OID_N,
          type: { enum: ['QUIZ', 'LAB', 'PROJECT', 'MIDTERM', 'FINAL', 'PRESENTATION', null] },
          phase: { enum: ['PERIODIC', 'MIDTERM', 'FINAL', null] },
          scheduledAt: DATE_N,
          weightInCourse: NUM_N,
          dataGranularity: en('ITEM', 'TOTAL_ONLY'),
          items: arr({
            bsonType: 'object', required: ['_id', 'code', 'kind', 'maxScore', 'outcomeMappings'],
            properties: {
              _id: OID, code: STR, title: STR_N,
              kind: en('QUESTION', 'CRITERION'),
              maxScore: NUM, bloomLevel: bloom, topic: STR_N,
              // Bản SNAPSHOT tại thời điểm thi: cặp (CLO, PI) và phần điểm của câu được chia cho cặp đó
              outcomeMappings: { bsonType: 'array', minItems: 1, items: OUTCOME_MAPPING },
              questionId: OID_N, questionVersion: INT_N,   // câu hỏi lấy từ ngân hàng (null với tiêu chí rubric hoặc đề ngoài ngân hàng)
              externalRef: OBJ_N, rubric: OBJ_N
            }
          }),
          source: OBJ_N, verification: VERIFICATION, extra: OBJ_N
        }),
        // Bản đánh giá theo lớp (scope=SECTION) bắt buộc có sectionId
        { $expr: { $or: [ { $ne: ['$scope', 'SECTION'] }, { $eq: [{ $type: '$sectionId' }, 'objectId'] } ] } },
        // Mỗi câu hỏi/tiêu chí: tổng phần điểm chia cho các cặp (CLO, PI) phải bằng 1
        { $expr: { $allElementsTrue: [ { $map: { input: { $ifNull: ['$items', []] }, as: 'it',
            in: { $and: [ sumsToOne('$$it.outcomeMappings.weight'), noDuplicatePairs('$$it.outcomeMappings') ] } } } ] } }
      ]
    },
    indexes: [
      [{ syllabusId: 1, sectionId: 1, code: 1 }, { unique: true }],
      [{ sectionId: 1 }],
      [{ 'items.outcomeMappings.cloId': 1 }],
      [{ 'items.outcomeMappings.piId': 1 }],
      [{ 'items.questionId': 1 }]
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
      studentRef: STR, cohort: STR_N, programVersionId: OID_N, fullName: STR_N
    }),
    indexes: [[{ studentRef: 1 }, { unique: true }], [{ cohort: 1 }]]
  },

  enrollments: {
    validator: schema(['sectionId', 'studentId', 'status'], {
      sectionId: OID, studentId: OID, status: en('ENROLLED', 'WITHDRAWN')
    }),
    indexes: [[{ sectionId: 1, studentId: 1 }, { unique: true }]]
  },

  // ----- Nhập điểm -----
  import_batches: {
    validator: schema(['sectionId', 'assessmentId', 'sourceSystem', 'status'], {
      sectionId: OID, assessmentId: OID,
      fileName: STR_N, fileChecksum: STR_N, fileRef: OBJ_N,
      sourceSystem: en('EXCEL', 'CSV', 'MOODLE_EXPORT'),
      columnMapping: OBJ_N,
      emptyCellAs: { enum: ['BLANK', 'MISSING', null] },
      reimportPolicy: { enum: ['INSERT_ONLY', 'UPSERT', 'REJECT_DUPLICATE', null] },
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
            required: ['sectionId', 'assessmentId', 'itemId', 'studentId', 'status', 'importBatchId'],
            properties: {
              sectionId: OID, assessmentId: OID, itemId: OID, studentId: OID,
              status: { enum: SCORE_STATUS_VALUES },
              rawScore: { bsonType: ['double', 'int', 'long', 'decimal', 'null'], minimum: 0 },
              maxScore: { bsonType: ['double', 'int', 'long', 'decimal'], minimum: 0 },
              rubricLevel: { bsonType: ['int', 'long', 'double', 'null'], minimum: 1, maximum: 4 },
              importBatchId: OID, sourceRow: INT_N, revision: INT_N
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
      [{ sectionId: 1, assessmentId: 1, itemId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1 }],
      [{ importBatchId: 1 }]
    ]
  },

  // ----- Đo lường -----
  measurement_rules: {
    validator: schema(['code', 'version', 'status'], {
      code: STR, version: STR,
      status: en('DRAFT', 'CONFIRMED', 'RETIRED'),
      confirmedBy: OID_N, confirmedAt: DATE_N,
      scope: OBJ_N, clo: OBJ_N, course: OBJ_N, missingDataPolicy: OBJ_N,
      pi: OBJ_N, plo: OBJ_N, cohort: OBJ_N, notes: STR_N
    }),
    indexes: [[{ code: 1, version: 1 }, { unique: true }]]
  },

  calculation_runs: {
    validator: schema(['scope', 'ruleId', 'status', 'lockKey'], {
      scope: OBJ, lockKey: STR, programVersionId: OID_N, syllabusIds: arr(OID), ruleId: OID,
      isOfficial: BOOL, dataSnapshot: OBJ_N, isStale: BOOL, staleReason: STR_N,
      status: en('RUNNING', 'SUCCEEDED', 'FAILED'),
      errorMessage: STR_N, triggeredBy: OID_N, startedAt: DATE_N, finishedAt: DATE_N
    }),
    indexes: [
      [{ ruleId: 1 }],
      [{ 'scope.sectionIds': 1, startedAt: -1 }],
      // hai lượt tính cùng phạm vi + cùng quy tắc không được chạy đồng thời
      [{ lockKey: 1 }, { unique: true, partialFilterExpression: { status: 'RUNNING' }, name: 'uniq_running_lockKey' }]
    ]
  },

  student_results: {
    validator: schema(['runId', 'level', 'subjectId', 'studentId', 'status'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID,
      sectionId: OID_N, studentId: OID, termId: OID_N,
      status: en('COMPUTED', 'INSUFFICIENT_DATA', 'NOT_APPLICABLE'),
      value: NUM_N, passed: { bsonType: ['bool', 'null'] },
      completeness: OBJ_N, inputs: arr(OBJ),
      ruleId: OID_N, programVersionId: OID_N, syllabusId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, sectionId: 1, studentId: 1 }, { unique: true }],
      [{ studentId: 1, level: 1, subjectId: 1, termId: 1 }]
    ]
  },

  aggregate_results: {
    validator: schema(['runId', 'level', 'subjectId', 'scope', 'counts'], {
      runId: OID, level: { enum: RESULT_LEVELS }, subjectId: OID, scope: OBJ, counts: OBJ,
      passRate: NUM_N, passRateOfEnrolled: NUM_N, targetPassRate: NUM_N,
      targetMet: { bsonType: ['bool', 'null'] }, dataCompleteness: NUM_N,
      ruleId: OID_N, programVersionId: OID_N
    }),
    indexes: [
      [{ runId: 1, level: 1, subjectId: 1, 'scope.type': 1, 'scope.sectionId': 1, 'scope.courseId': 1,
         'scope.cohort': 1, 'scope.termId': 1 }, { unique: true, name: 'uniq_aggregate_scope' }],
      [{ level: 1, subjectId: 1, 'scope.termId': 1 }]
    ]
  },

  // ----- Báo cáo, cải tiến, hỗ trợ -----
  reports: {
    validator: schema(['type', 'runId'], {
      type: en('SECTION_CLO', 'COURSE_PI', 'PROGRAM_PLO', 'DATA_COMPLETENESS'),
      title: STR_N, params: OBJ_N, runId: OID, ruleId: OID_N, programVersionId: OID_N,
      summary: OBJ_N, limitations: STR_N, fileRef: OBJ_N, exports: arr(OBJ),
      generatedBy: OID_N, generatedAt: DATE_N
    }),
    indexes: [[{ runId: 1 }], [{ type: 1, generatedAt: -1 }]]
  },

  improvement_actions: {
    validator: schema(['issue', 'ownerId', 'status'], {
      issue: OBJ, action: STR_N, ownerId: OID, dueDate: DATE_N,
      priority: { enum: ['LOW', 'MEDIUM', 'HIGH', null] },
      status: en('OPEN', 'IN_PROGRESS', 'DONE', 'VERIFIED', 'CANCELLED'),
      completionNote: STR_N, history: arr(OBJ)
    }),
    indexes: [[{ ownerId: 1, status: 1 }], [{ 'issue.linkedId': 1 }], [{ dueDate: 1 }]]
  },

  audit_logs: {
    validator: schema(['at', 'action', 'entity'], {
      at: DATE, userId: OID_N,
      action: en('SCORE_UPDATE', 'IMPORT_CONFIRM', 'RUN_CALCULATION', 'RULE_CONFIRM', 'LOGIN'),
      entity: OBJ, before: OBJ_N, after: OBJ_N, reason: STR_N, ip: STR_N
    }),
    indexes: [[{ 'entity.collection': 1, 'entity.id': 1, at: -1 }], [{ userId: 1, at: -1 }]]
  },

  ml_experiments: {
    validator: schema(['name', 'status'], {
      name: STR, datasetSnapshot: OBJ_N, features: arr(STR), model: STR_N, metrics: OBJ_N,
      status: en('DRAFT', 'DONE'), disclaimer: STR_N
    }),
    indexes: [[{ name: 1 }]]
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
  applicableCohorts: [], status: 'DRAFT',
  verification: { status: 'NEEDS_CLARIFICATION', verifiedBy: null, verifiedAt: null,
                  note: 'Chờ xác nhận phiên bản áp dụng cho khóa nào (Q01).' }
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
    displayOrder: p[1], contentOriginal: null, contentSummary: p[3], capabilityLevelRaw: p[2],
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
  clo: { method: 'WEIGHTED_NORMALIZED_SCORE', passThreshold: null, minCoverage: null },
  course: { requireAllCloPass: null, cloWeightSource: 'clos.measurement.weightInCourse' },
  missingDataPolicy: {
    BLANK: 'COUNT_AS_ZERO', ABSENT: 'EXCLUDE_AND_FLAG', NOT_GRADED: 'INSUFFICIENT_DATA',
    EXEMPT: 'EXCLUDE', MISSING: 'INSUFFICIENT_DATA'
  },
  pi: { method: 'DIRECT_FROM_ITEMS', passThreshold: null },
  plo: { method: null, passThreshold: null },
  cohort: { denominator: null, targetPassRate: null },
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
 'course_pi_contributions', 'measurement_rules', 'syllabi', 'clos', 'question_banks', 'questions'].forEach(function (n) {
  print('      ' + n + ': ' + d.getCollection(n).countDocuments({}));
});
print('>>> HOÀN TẤT. Tổng số collection: ' + d.getCollectionNames().length);
