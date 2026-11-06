# ADR 0012 — Không quy đổi I/R/M thành hệ số; bỏ `useIrmAsWeight` và `irmWeights` (Q18)

- **Trạng thái:** ĐÃ CHẤP NHẬN (ACCEPTED) theo **quyết định của nhóm sinh viên** ngày 2026-11-06.
  **Chưa có xác nhận của GVHD/Khoa**; nếu Khoa yêu cầu quy đổi thì viết ADR mới thay thế.
- **Ngày:** 2026-11-06
- **Người soạn:** Antigravity theo yêu cầu của sinh viên Minh (`minhat5`); sinh viên rà soát và chốt: [ĐIỀN]
- **Liên quan:** `GiaiThich_CoSoDuLieu.md` nguyên tắc 5, §4.23, §8 (Q18); `PLO_DB_UPDATE.md` §4.2;
  `.claude/rules/02-domain-invariants.md` ("I/R/M ≠ trọng số"); ADR 0011

## 1. Bối cảnh

CSDL v0.5 thêm `measurement_rules.pi.useIrmAsWeight` (mặc định `false`) và `irmWeights {I, R, M}`, cho phép nhân
hệ số I/R/M vào công thức `FROM_CLOS`. Điều này trái nguyên tắc 5 ("I/R/M là vai trò sư phạm, không phải trọng số")
nếu bật lên. Trước đây engine luôn coi cờ là `false` nhưng trường vẫn nằm trong lược đồ, MongoDB vẫn kiểm tra nó và
hội đồng có thể hỏi vì sao có một trường không bao giờ được dùng.

## 2. Quyết định

- Không quy đổi I/R/M thành hệ số trong bất kỳ công thức nào.
- **Xóa** `pi.useIrmAsWeight`, `pi.irmWeights` khỏi `measurement_rules` (validator, seed, tài liệu) và xóa điều kiện
  "bật cờ thì phải đủ ba hệ số".
- Công thức `FROM_CLOS` bỏ `IRM_Factor` (xem ADR 0011 §2).
- `course_pi_contributions` (ma trận I/R/M) giữ nguyên, chỉ để hiển thị và đối chiếu.

## 3. Hệ quả

- Lược đồ gọn hơn; không còn trường "chết". Mã Java chưa dùng hai trường này nên không phải sửa mã.
- Ca kiểm thử CSDL "`useIrmAsWeight = true` thiếu `irmWeights` bị từ chối" và US-26/AC-4 bị bỏ.
- Nếu CSDL cục bộ đã nạp bản cũ: chạy lại `database_import.js` (hoặc dựng lại image `docker/mongo/`) để cập nhật
  validator.
