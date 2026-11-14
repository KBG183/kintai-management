# 활성 프로젝트 경로

이 파일은 작업 중 사용하는 현재 경로를 기록하는 운영용 경로표다. 과거 증적에 남은 이전 경로는 역사적 기록으로 취급하고, 아래 경로를 현재 기준으로 사용한다.

| 영역 | 현재 경로 |
|---|---|
| 프로젝트 규칙 | `C:\dev\kintai\AGENTS.md` |
| SRS | `C:\dev\kintai\kintai_requirements_specification_revised.md` |
| RTM | `C:\dev\kintai\docs\rtm_ledger.md` |
| 요구사항 추적 규칙 | `C:\dev\kintai\docs\requirements_tracking_process.md` |
| 한국어 증적서 | `C:\dev\kintai\docs\portfolio_audit_evidence_KO.md` |
| 일본어 증적서 | `C:\dev\kintai\docs\portfolio_audit_evidence.md` |
| 일별 감사 결과 | `C:\dev\kintai\audit_reports\day_by_day\` |
| 독립 감사 번들 | `C:\dev\kintai\docs\audit_bundles\` |
| 최종 포트폴리오 | `C:\dev\kintai\docs\final-portfolio\` |
| 감사 실행 결과 | `C:\dev\kintai\docs\audit_runs\` |
| 역사적 Day 10 사용자 프로필 Hermes 스크립트 | `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1` (과거 실행 해시 증거 전용; 현재 프로젝트 정본이 아님) |
| 현재 프로젝트 감사 진입점 | `C:\dev\kintai\scripts\codex_hermes_bridge.ps1` → `scripts\audit_with_hermes.ps1` (설정 endpoint/model 사용, HTTP 기본값) |
| 선택적 사전검증 진입점 | `C:\dev\kintai\scripts\audit_hermes_preflight.ps1` (프로젝트 감사 스크립트 실행 전 입력 검증) |
| 보관 감사 문서 | `C:\dev\kintai\docs\legacy_audit_reports\` |
| 학습·참고 문서 | `C:\dev\kintai\docs\study_guides\` |
| 작업 로그 | `C:\dev\kintai\docs\logs\` |
| 작업·인계 문서 | `C:\dev\kintai\docs\workflow\` 및 `C:\dev\kintai\docs\working\` |

## 경로 변경 규칙

경로를 이동하거나 이름을 바꾸면 이 파일을 먼저 갱신하고, 스크립트·활성 문서·증적서의 참조를 확인한다. 이동 전후 파일 해시가 다르면 단순 이동으로 처리하지 않고 내용 또는 인코딩 변경으로 기록한다.
