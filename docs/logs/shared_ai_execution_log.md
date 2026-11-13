# Shared AI Execution and Error Log

이 문서는 Codex와 Antigravity가 공용으로 사용하는 실행·오류·수정 이력이다.
기능 Finding 원장인 `docs\audit_ledger.csv`와 분리하여, AI 오퍼레이터·Hermes·Qwen 큐·실행 환경의 문제를 기록한다.

## 운영 규칙

- Codex와 Antigravity 중 실제 작업을 수행한 오퍼레이터가 기록한다.
- 오류 발생, 원인 확인, 수정, 재검증을 한 묶음으로 남긴다.
- 기존 기록은 삭제하거나 덮어쓰지 않는다.
- Qwen 감사 결과는 실행 ID와 결과 파일 경로를 함께 기록한다.
- 기능 코드 오류와 AI 운영 인프라 오류를 구분한다.

## 2026-11-12 ~ 2026-11-13: Codex 원격 Qwen 큐 구축

| ID | 단계 | 문제 또는 작업 | 조치·결과 | 증적 |
|---|---|---|---|---|
| AI-001 | Codex 직접 호출 | ChatGPT Codex 샌드박스에서 Tailscale `100.111.193.94:11434` 소켓 접근 거부 | 직접 호출 불가 확인. Windows 파일 큐 중계 방식으로 전환 | `docs/codex_to_antigravity_remote_qwen_handoff.md` |
| AI-002 | 큐 1차 테스트 | `raw_response.json` 저장 전 바이트와 기록 SHA-256 불일치 | 디스크 저장 후 실제 파일을 다시 해시하도록 수정 | `docs/codex_queue/completed/codex_test_remote_ok/` |
| AI-003 | 큐 1차 테스트 | `result.md`에 Qwen `<think>` 흔적 노출 | 결과 저장 전 사고 과정 태그 제거 | 동일 경로 `result.md` |
| AI-004 | 실패 처리 | 잘못된 JSON·localhost·로컬 7B 요청의 실패 원장 누락 위험 | `failed/<request-id>/request.json`, `error.txt`, `manifest.json` 강제 생성 | `docs/codex_queue/failed/` |
| AI-005 | 보안 검수 | endpoint SSRF 및 `prompt_file` 경로 탈출 방어 필요 | Tailscale endpoint 화이트리스트와 프로젝트 루트 경계 검증 추가 | `scripts/process_codex_queue.ps1` |
| AI-006 | 보안 검수 | `request_id`를 폴더명으로 직접 사용하면 경로 탈출 가능 | 영문·숫자·밑줄·하이픈 화이트리스트와 안전 격리 폴더 적용 | `failed/invalid_id_traversal/` |
| AI-007 | 최종 재검증 | 정상 원격 호출과 실패 케이스 회귀 확인 | `REMOTE_OK`, SHA-256 3중 일치, 실패 격리 확인 | `completed/codex_test_remote_ok/`, `failed/` |

## 다음 기록 대상

- Day 10 동시성 테스트 실행 결과
- Day 10 MockMvc 권한 테스트 실행 결과
- 테스트 실패 원인과 수정 파일
- Qwen 독립 감사 실행 ID와 결과
- 사용자 승인 대기 또는 미해결 위험
