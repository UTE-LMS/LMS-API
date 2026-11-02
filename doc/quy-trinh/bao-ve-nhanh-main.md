# Hướng dẫn bật bảo vệ nhánh `main` (TD-10)

> **Dự thảo do AI soạn; người dùng đã quyết định chỉ viết hướng dẫn.** AI **không** gọi API để đổi cài đặt GitHub; mọi lệnh
> ghi dưới đây do **sinh viên có quyền admin tự chạy**, sau khi đọc mục "Rủi ro". Tài liệu cũng **không khẳng định** nhánh `main` đã
> được bảo vệ: tới khi sinh viên chạy và kiểm tra (mục 6), `doc/tech-debt.md` TD-10 vẫn là "Mở".
> Căn cứ: `.claude/rules/03-code-quality-and-git.md` (mục Git: "Cấu hình bảo vệ nhánh `main` (bắt buộc ≥ 1 review)"), issue
> `LMS-API#15`, TD-10 trong `doc/tech-debt.md`. Áp dụng cho **hai repo**: `UTE-LMS/LMS-API` và `UTE-LMS/LMS-UI`.

## 1. Hiện trạng đã quan sát (chỉ đọc, ngày 2026-11-02)

Bằng các lệnh `gh api` chỉ đọc (GET) bởi tài khoản đang đăng nhập `gh` của người dùng:

| Mục | `LMS-API` | `LMS-UI` |
|---|---|---|
| Nhánh mặc định / công khai | `main` / công khai | `main` / công khai |
| `GET …/branches/main/protection` | HTTP 404 "Branch not protected" | HTTP 404 "Branch not protected" |
| Người cộng tác | 2 tài khoản, cả hai có quyền `admin` | (cùng tổ chức; chưa liệt kê riêng) |
| Tên check do CI báo trên commit `main` | `build-test` (ứng dụng `github-actions`, id 15368) | `build-test` (ứng dụng `github-actions`, id 15368) |

Ngoài ra `gh pr list -R UTE-LMS/LMS-API --state merged` (9 PR đã merge được liệt kê) cho thấy **không PR nào có review**, và PR #10 do cùng một tài khoản
mở và merge. Đây là lý do chỉ số "thay đổi qua PR có review ≥ 90%" (TC2.4) chưa được bảo đảm.

> Tên check `build-test` là tên **job** trong workflow `CI` của hai repo (`.github/workflows/ci.yml`) tại thời điểm trên. Khi nhóm
> phụ trách CI sửa workflow (đổi tên job, tách job, thêm job lint/security…), tên check **có thể đổi** — xem mục 5.

## 2. Cấu hình đề xuất

| Cài đặt | Đề xuất | Lý do |
|---|---|---|
| Bắt buộc Pull Request trước khi merge | Bật | Chặn push trực tiếp vào `main` |
| Số approve tối thiểu | 1 | Rule 03; người mở PR không tự approve được (GitHub chặn), nên người còn lại phải review |
| Bỏ approve cũ khi có commit mới (`dismiss_stale_reviews`) | Bật (có thể tắt nếu thấy phiền) | Review phải áp dụng cho mã cuối cùng |
| Yêu cầu check CI | `build-test` | Không merge khi build/test đỏ |
| Nhánh phải cập nhật mới nhất (`strict`) | Tắt lúc đầu | Bật sẽ buộc rebase/merge `main` nhiều lần, tốn công với nhóm 2 người |
| Yêu cầu giải quyết hết bình luận | Bật | Tránh bỏ sót góp ý review |
| Cấm force-push, cấm xóa nhánh `main` | Bật (`allow_force_pushes=false`, `allow_deletions=false`) | Rule 03: không rewrite lịch sử |
| Áp dụng cho cả admin (`enforce_admins`) | **Cân nhắc** — xem mục 4 | Cả hai sinh viên đều là admin: nếu không bật thì chính họ vẫn merge vượt được |
| Giới hạn ai được push (`restrictions`) | Không dùng | Không cần cho 2 người; PR đã đủ |

## 3. Cách làm

### 3.1 Bằng giao diện GitHub (cho từng repo)
Tên nhãn dưới đây theo giao diện tiếng Anh hiện tại của GitHub và có thể đổi; ý nghĩa tương đương nếu nhãn khác đi.

1. Mở repo → **Settings** → **Branches** → **Add branch ruleset** hoặc **Add classic branch protection rule** (hướng dẫn này theo cách *classic*; GitHub cũng có *Rulesets* với cùng ý tưởng).
2. **Branch name pattern:** `main`.
3. Tích **Require a pull request before merging** → **Required approvals: 1** → (tùy chọn) **Dismiss stale pull request approvals when new commits are pushed**.
4. Tích **Require status checks to pass before merging** → tìm và chọn **`build-test`** (chỉ hiện sau khi check đã chạy ít nhất một lần trong repo). Để **Require branches to be up to date** tắt lúc đầu.
5. Tích **Require conversation resolution before merging**.
6. Bỏ trống (không tích) **Allow force pushes** và **Allow deletions**.
7. **Do not allow bypassing the above settings** (tương ứng `enforce_admins`): tích hay không là quyết định ở mục 4.
8. **Create** / **Save changes**.

### 3.2 Bằng `gh api` (in sẵn lệnh; **chưa chạy bởi AI**)

Điều kiện: đã `gh auth login` bằng tài khoản có quyền admin repo. Lệnh `PUT` **thay toàn bộ** cấu hình bảo vệ của nhánh, nên chạy
từng repo một và đọc lại kết quả (mục 6) ngay sau đó.

**`UTE-LMS/LMS-API`**

```bash
gh api -X PUT repos/UTE-LMS/LMS-API/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "checks": [ { "context": "build-test" } ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

**`UTE-LMS/LMS-UI`** (cùng nội dung, đổi repo)

```bash
gh api -X PUT repos/UTE-LMS/LMS-UI/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "checks": [ { "context": "build-test" } ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

Ghi chú về JSON: `required_status_checks`, `enforce_admins`, `required_pull_request_reviews`, `restrictions` là các khóa bắt buộc của
endpoint (đặt `null` nếu không dùng); `checks[].context` là tên check; không đặt `app_id` thì GitHub tự chọn ứng dụng đã báo check đó
gần đây. Trên PowerShell, dùng here-string `@' … '@` và `| gh api -X PUT … --input -`; hoặc lưu JSON ra file rồi `--input file.json`.

**Bật/tắt `enforce_admins` riêng (không đụng phần còn lại):**

```bash
# bật (admin cũng phải qua PR + review + check)
gh api -X POST   repos/UTE-LMS/LMS-API/branches/main/protection/enforce_admins
# tắt
gh api -X DELETE repos/UTE-LMS/LMS-API/branches/main/protection/enforce_admins
# lặp lại với repos/UTE-LMS/LMS-UI/…
```

**Sửa tên check bắt buộc khi CI đổi (ví dụ job mới tên `ci`):**

```bash
gh api -X PATCH repos/UTE-LMS/LMS-API/branches/main/protection/required_status_checks \
  --input - <<'JSON'
{ "strict": false, "checks": [ { "context": "ten-job-moi" } ] }
JSON
```

**Lối thoát khi bị kẹt (xem mục 4) — chỉ dùng có chủ đích, ghi lại lý do:**

```bash
# gỡ riêng yêu cầu review
gh api -X DELETE repos/UTE-LMS/LMS-API/branches/main/protection/required_pull_request_reviews
# hoặc gỡ toàn bộ bảo vệ của nhánh
gh api -X DELETE repos/UTE-LMS/LMS-API/branches/main/protection
```

## 4. Cân nhắc `enforce_admins` và rủi ro tự khóa khi nhóm chỉ có 2 người

- **Nếu `enforce_admins=false`:** cả hai sinh viên (đều là admin) vẫn có thể bấm "merge without waiting for requirements" hoặc push thẳng.
  Bảo vệ khi đó chỉ là *kỷ luật tự giác*; chỉ số "≥ 90% PR có review" vẫn có thể bị phá. Ưu điểm: không bao giờ tự khóa mình.
- **Nếu `enforce_admins=true`:** không ai vượt được quy tắc, nhưng nhóm 2 người dễ **tự khóa**:
  1. Một người vắng/mất quyền → người kia mở PR không thể tự approve nên **không merge được** bất cứ thứ gì (kể cả bản sửa khẩn).
  2. Check bắt buộc đỏ do lỗi hạ tầng CI (không phải lỗi mã) → không có đường vòng.
  3. Đổi tên check mà chưa cập nhật bảo vệ → PR treo ở "Expected — Waiting for status to be reported".
  Lối thoát là một admin chạy lệnh DELETE/PATCH ở mục 3.2 (tắt `enforce_admins`, gỡ review hoặc sửa tên check), rồi **bật lại ngay**
  và ghi lý do vào PR/issue để lịch sử đáng tin.
- **Đề xuất của AI (nhóm tự quyết):** bắt đầu với `enforce_admins=false`, hoàn tất ít nhất một PR có review và check xanh đúng quy trình;
  khi cả hai đã quen và cần chứng minh chỉ số review, chuyển sang `true`, giữ sẵn lệnh lối thoát. Nếu GVHD/hội đồng yêu cầu bảo
  đảm cứng thì bật `true` từ đầu.
- **Quy tắc phối hợp:** mọi PR do AI hỗ trợ vẫn được mở bằng tài khoản của một sinh viên nên sinh viên còn lại phải review và approve;
  AI không approve/merge thay (`CLAUDE.md`).

Các rủi ro khác cần biết:
- **Check không bao giờ báo cáo:** nếu sau này workflow dùng `paths:` hoặc `branches:` lọc sự kiện `pull_request` làm job `build-test` không chạy
  với PR chỉ sửa tài liệu, PR đó sẽ treo vì thiếu check bắt buộc. Hiện `ci.yml` của API kích hoạt `pull_request` cho mọi nhánh; của UI
  kích hoạt `pull_request` không lọc nhánh (nhưng `push` chỉ trên `main`). Kiểm tra lại mỗi khi sửa workflow.
- **`strict=true`** buộc cập nhật nhánh với `main` trước khi merge; với 2 người dễ gây vòng lặp rebase. Chỉ bật khi có nhiều PR chạy song song.
- **`dismiss_stale_reviews=true`** buộc xin approve lại sau mỗi commit mới: an toàn hơn nhưng chậm hơn.
- **Xóa/đổi tên nhánh `main` hoặc chuyển repo sang tổ chức khác** có thể làm mất quy tắc; kiểm tra lại sau thay đổi như vậy.

## 5. Phối hợp với nhóm CI (tên check có thể đổi)

Lệnh ở mục 3 gắn cứng tên `build-test`. Thứ tự an toàn khi một PR đổi tên/tách job trong `.github/workflows/ci.yml`:

1. Trước khi merge PR đổi workflow, xem tên check mà PR đó thực sự báo (tab Checks của PR, hoặc
   `gh api repos/UTE-LMS/LMS-API/commits/<sha>/check-runs --jq '.check_runs[].name'`).
2. Merge PR; ngay sau đó chạy lệnh `PATCH …/required_status_checks` với tên mới (hoặc tạm gỡ yêu cầu check bằng giao diện).
3. Kiểm tra lại bằng lệnh ở mục 6 và mở một PR thử.

Danh sách check mong muốn dài hạn (khi pipeline có đủ chặng: lint, test, quét bảo mật…) là việc của nhóm CI; nên liệt kê tất cả check
phải xanh, nhưng chỉ thêm khi chúng đã chạy ổn định, để khỏi chặn nhầm.

## 6. Cách kiểm tra đã bật (chỉ đọc)

```bash
# tóm tắt nhanh
gh api repos/UTE-LMS/LMS-API/branches/main --jq '{protected: .protected}'

# chi tiết cấu hình
gh api repos/UTE-LMS/LMS-API/branches/main/protection --jq '{
  approvals:        .required_pull_request_reviews.required_approving_review_count,
  dismiss_stale:    .required_pull_request_reviews.dismiss_stale_reviews,
  checks:           [.required_status_checks.checks[].context],
  strict:           .required_status_checks.strict,
  enforce_admins:   .enforce_admins.enabled,
  force_pushes:     .allow_force_pushes.enabled,
  deletions:        .allow_deletions.enabled,
  conversations:    .required_conversation_resolution.enabled
}'

# lặp lại cho UI
gh api repos/UTE-LMS/LMS-UI/branches/main/protection --jq '{approvals: .required_pull_request_reviews.required_approving_review_count, checks: [.required_status_checks.checks[].context], enforce_admins: .enforce_admins.enabled}'
```

Kết quả mong đợi: `approvals = 1`, `checks = ["build-test"]` (hoặc tên đã cập nhật), `force_pushes = false`, `deletions = false`.
Trước khi bật, lệnh `…/protection` trả 404 "Branch not protected" (đúng như mục 1).

Kiểm tra hành vi thật (nên làm một lần): mở một PR nhỏ từ nhánh `docs/…`; xác nhận nút merge bị chặn cho tới khi (a) người còn lại
approve và (b) check `build-test` xanh; và xác nhận `git push origin main` trực tiếp bị từ chối khi `enforce_admins=true`
(với `false`, admin có thể vẫn push được — đó là lý do cân nhắc ở mục 4). **Không** kiểm bằng cách push mã thật lên `main`.

## 7. Sau khi bật (việc của sinh viên)

1. Lưu minh chứng: kết quả lệnh GET (đã ẩn thông tin không cần) hoặc ảnh chụp trang Settings → Branches, kèm ngày; đặt trong hồ sơ nộp.
2. Cập nhật `doc/tech-debt.md` (TD-10: chuyển trạng thái, điền phụ trách/hạn) và `doc/rubric-checklist.md` (TC2.4) bằng minh chứng thật
   — AI không tự ghi "đã bật".
3. Ghi quyết định `enforce_admins` và lý do (một dòng trong PR hoặc ADR ngắn nếu đổi quy trình).
4. Cập nhật mục "Git" của `doc/coding-convention.md` nếu quy trình review thay đổi.
