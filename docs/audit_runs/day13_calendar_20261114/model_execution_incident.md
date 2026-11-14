# 감사 실행 경로 정정 기록

- 사용자 지시: 로컬 7B를 끄고 원격 27B를 사용하며, 원격 주소는 100.111.193.94.
- 에이전트 실수: 과거 100.91 주소를 사용하려고 했고, 27B 사용 지시를 잘못 해석해 원격 호출 대신 로컬 `qwen3.8:27b` 다운로드를 시작함.
- 조치: 잘못된 로컬 pull은 약 7GB 진행 후 중지. 다시 시작하지 않았고 감사에 사용하지 않음. 로컬 7B 보충 실행도 중단, 결과 없음. 기존 7B는 실행 중 프로세스가 없음을 확인함.
- 캐시: 부분 다운로드 파일을 삭제하지 않음. 다른 모델이 공유하는 블롭일 수 있어 보존.
- 올바른 원격 확인: `http://100.111.193.94:11434/api/tags`, `qwen27b:latest`, 27.3B, IQ3_M, digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`.
- 최종 원격 감사: `qwen27b_reaudit/`, Run ID `20261113_230538_8ce60d45`, Day 13 범위 PASS. 입력 및 결과 해시는 RTM R-18과 인수인계에 기록.
- 원본 실패/중단 증거: `qwen27b_local_pull.log`, `qwen7b_supplemental_console.log`, 첫 감사 `qwen27b_final/`.

## Qwen pull cache cleanup follow-up (2026-11-14 KST)

The previously incomplete local `qwen3.8:27b` pull was never installed or used as an auditor. At the user's explicit request, the 21 exact unreferenced partial-download blobs were removed after checking the local Ollama process list/API had no loaded model and confirming the blobs were not referenced by installed manifests. The installed `qwen2.5:7b` model was preserved. Cleanup evidence: `qwen_local_pull_cleanup_manifest.json` (`cleanup_status: DELETED`, 21 artifacts, zero remaining).