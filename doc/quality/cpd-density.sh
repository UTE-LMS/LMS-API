#!/usr/bin/env bash
# Tính tỷ lệ dòng trùng lặp (%) từ báo cáo PMD CPD để so với ngưỡng <= 3% của Rubric TC2.4.
#
# CPD chỉ liệt kê các khối trùng, không in phần trăm. Script này lấy hợp các dòng nằm trong
# MỌI khối trùng (mỗi dòng đếm một lần, kể cả khi trùng với nhiều nơi) rồi chia cho tổng số dòng
# của các tệp .java. Cách đếm này gần với "duplicated_lines_density" của SonarQube nhưng KHÔNG
# phải con số chính thức của Sonar; số chính thức chỉ có sau khi CI đẩy lên SonarCloud.
#
# Cách dùng (từ thư mục gốc repo, sau khi chạy `./mvnw -B -ntp pmd:cpd-check`):
#   bash doc/quality/cpd-density.sh [target/cpd.xml]
set -euo pipefail

report="${1:-target/cpd.xml}"
if [[ ! -f "$report" ]]; then
  echo "Không thấy $report. Chạy ./mvnw -B -ntp pmd:cpd-check trước." >&2
  exit 2
fi

# Danh sách dòng trùng, dạng "<loại>\t<đường dẫn>\t<số dòng>", mỗi dòng một bản ghi duy nhất.
duplicated="$(
  awk '
    /<duplication / { in_dup = 1 }
    /<\/duplication>/ { in_dup = 0 }
    in_dup && /^ *(<file )?begintoken=/ { start = ""; end = "" }
    in_dup && /endline="/ { match($0, /endline="[0-9]+"/); end = substr($0, RSTART + 9, RLENGTH - 10) + 0 }
    in_dup && /^ *line="/ { match($0, /line="[0-9]+"/); start = substr($0, RSTART + 6, RLENGTH - 7) + 0 }
    in_dup && /path="/ {
      match($0, /path="[^"]*"/); p = substr($0, RSTART + 6, RLENGTH - 7)
      for (i = start; i <= end; i++) seen[p "\t" i] = 1
    }
    END { for (k in seen) print k }
  ' "$report"
)"

count_for() { # $1 = mẫu đường dẫn (main|test)
  if [[ -z "$duplicated" ]]; then echo 0; return; fi
  printf '%s\n' "$duplicated" | grep -c "src[\\/]$1[\\/]" || true
}

total_for() {
  find "src/$1/java" -name '*.java' -print0 | xargs -0 cat | wc -l | tr -d ' '
}

for scope in main test; do
  dup="$(count_for "$scope")"
  tot="$(total_for "$scope")"
  awk -v s="$scope" -v d="$dup" -v t="$tot" \
    'BEGIN { printf "%-5s dòng trùng=%d / tổng=%d => %.2f%%\n", s, d, t, (t ? 100 * d / t : 0) }'
done
dup_all=$(( $(count_for main) + $(count_for test) ))
tot_all=$(( $(total_for main) + $(total_for test) ))
awk -v d="$dup_all" -v t="$tot_all" \
  'BEGIN { printf "all   dòng trùng=%d / tổng=%d => %.2f%% (ngưỡng <= 3%%)\n", d, t, (t ? 100 * d / t : 0) }'
