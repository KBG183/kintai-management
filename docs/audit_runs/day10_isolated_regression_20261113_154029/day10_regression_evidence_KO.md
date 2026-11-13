# Day 10 격리 MySQL 반복 회귀 증적

## 판정

- 대상 테스트: `com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest`
- 실행 환경: Windows 11, Java 17.0.12, Gradle 9.8.1, MySQL 26.7.0
- 스키마: `src/main/resources/db/schema.sql` rev.3에서 DB 선택 구문만 제거한 격리 적용본
- 운영 DB `kintai_db`: 접근하지 않음
- 반복 실행: 3회
- 총 테스트: 6건
- 성공: 6건
- 실패: 0건
- 오류: 0건
- 최종 판정: `LOCAL_MYSQL_REPEAT_VERIFIED`
- MySQL 8.4 Testcontainers: `BLOCKED` (Docker CLI/환경 미확보 상태 유지)
- Qwen 독립 감사: 이번 회귀 실행에서는 수행하지 않음

## 회차별 결과

| 회차 | 격리 DB | 테스트 | 실패 | 오류 | 종료 코드 | 판정 |
|---:|---|---:|---:|---:|---:|---|
| 1 | `kintai_day10_test_858b35511a0d` | 2 | 0 | 0 | 0 | SUCCESS |
| 2 | `kintai_day10_test_e6c1a49494b7` | 2 | 0 | 0 | 0 | SUCCESS |
| 3 | `kintai_day10_test_934cea2c347d` | 2 | 0 | 0 | 0 | SUCCESS |

## 증적 SHA-256

| 회차 | JUnit XML SHA-256 | 실행 로그 SHA-256 |
|---:|---|---|
| 1 | `ed9848e9b84c1bc0ff740cf92121d39928da74cc844ce6d800b5c48e5aac5dda` | `cb69a27ec94667e04347190ee0fc28809e4e90d21522d68a3f76c28446339789` |
| 2 | `6874e4a0db9e86a81ac47af2257fea567fec8ff770aae336d9cd4952ff914205` | `9239039f2a78f89ff0a30b9f6abdf5f0ea0e814985c0b49d91455ea86467104e` |
| 3 | `b550c5bd1a5a57b70d5f739364865ebdabb5c83a0615aaaa7eda58b451f24c33` | `04577319fa9c7050d06178cfc1d8469403d354eeb8cdc8af527d4526116c3bdc` |

## 실패 이력 및 보정

1. 최초 격리 실행은 구형 `schema.sql`의 `break_id`와 Mapper의 `attendance_break_id` 불일치로 실패했다.
2. JDBC URL의 `&` 옵션 전달은 Windows 환경변수 처리와 충돌하여 단순 URL로 보정했다.
3. 이후 정식 rev.3 스키마 기준 새 격리 DB에서 3회 반복 실행을 완료했다.

실패 이력은 삭제하지 않으며, 최종 성공 판정에는 포함하지 않고 원인·보정 경로로 보존한다.
