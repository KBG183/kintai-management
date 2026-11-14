# Codex 인수인계: 스키마 정본 통합 및 재검증 대기

## 변경 완료

- 정본: `src/main/resources/db/schema.sql` rev.3
- 정본에서 `CREATE DATABASE`와 `USE` 구문 제거
- 구형 파일 이동: `docs/legacy_audit_reports/schema.sql_legacy_20261113.sql`
- 구형 파일 SHA-256: `9B890EF89861DA789238A47D89E626A969934E1782489C7BD1412EFB31F9154F`
- 기능 코드 수정: 0건

## 확인된 참조

- 준비·전체 테스트 스크립트는 이미 `src/main/resources/db/schema.sql`을 사용했다.
- 애플리케이션·테스트 코드에서 구형 `src/main/resources/schema.sql`을 직접 참조하는 실행 경로는 확인되지 않았다.
- 과거 감사 문서의 구형 스키마 언급은 역사적 증적으로 유지한다.

## 현재 상태

- 상태: `SCHEMA_REVALIDATION_PENDING`
- 이전 rev.3 기준 전체 회귀: 79/79 PASS
- 이번 정본 이동 후 전체 회귀: 아직 실행 전

## 다음 단계

1. 신규 격리 DB 생성
2. 정본 rev.3 적용
3. 전체 fixture 적재
4. `.\gradlew.bat test --rerun-tasks --no-daemon --console plain` 실행
5. 79건, JUnit XML 33개, 종료 코드 0 확인
6. 운영 DB 미접촉 확인
7. 한·일 증적서·RTM·인수인계서의 `SCHEMA_REVALIDATION_PENDING`을 최종 결과로 갱신
