# Codex → Antigravity 원격 Qwen 27B 호출 브리지 추가 요청

## 목적

Antigravity에서 이미 사용 중인 Tailscale 원격 Ollama의 Qwen 27B를, ChatGPT 내부 Codex가 만든 감사 요청에도 공용으로 사용하고 싶다.

중요: Codex 내부 실행 환경이 Tailscale 네트워크에 직접 접근하도록 만드는 것이 아니라, **사용자 Windows에서 실행되는 중계 프로세스가 원격 Ollama를 호출하고 결과를 공용 프로젝트 폴더에 저장**하는 구조로 구현한다.

## 현재 원격 모델 정보

- Tailscale 원격 Ollama: `http://100.111.193.94:11434`
- 모델: `qwen27b:latest`
- 원격 PC의 Ollama API/TCP는 사용자 측에서 정상 사용 중
- 이 PC의 로컬 Ollama에는 `qwen2.5:7b`만 있음
- 로컬 `qwen2.5:7b`를 27B 대체 모델로 사용하면 안 됨
- 이전 주소 `100.91.26.37`은 사용하지 않음(offline 상태 기록)

## Codex에서 실제 발생한 호출 로그

### 요청

```text
POST http://100.111.193.94:11434/api/generate
Content-Type: application/json

{
  "model": "qwen27b:latest",
  "prompt": "Reply with exactly REMOTE_OK",
  "stream": false
}
```

### 결과

```text
실패
원인: 100.111.193.94:11434 소켓 접근 권한 거부
```

이 오류는 모델명이나 Ollama API 형식 오류가 아니라 **ChatGPT 내부 Codex 샌드박스의 Tailscale 네트워크 접근 차단**이다. Codex 코드만 수정해서 이 권한을 해제할 수 없다.

## 현재 Codex가 수정한 파일

- `scripts\codex_hermes_bridge.ps1`
- `scripts\run_codex_hermes_host.ps1`
- `docs\codex_hermes_bridge_usage.md`

`run_codex_hermes_host.ps1`는 현재 다음 방식으로 수정되어 있다.

- 로컬 `ollama list`를 검사하지 않음
- 로컬 `qwen2.5:7b`를 호출하지 않음
- `scripts\audit_config.json`의 `primary_auditor.endpoint`를 사용
- 원격 HTTP 전송만 선택
- 실행 결과를 `docs\audit_runs\codex_<run-id>\`에 저장

## Antigravity에 구현 요청하는 구조

### 1. Codex 요청 큐

```text
docs\codex_queue\pending\<request-id>.json
```

요청 JSON에는 최소한 다음 필드를 포함한다.

```json
{
  "request_id": "codex_yyyyMMdd_HHmmss_xxxx",
  "model": "qwen27b:latest",
  "endpoint": "http://100.111.193.94:11434",
  "prompt_file": "...",
  "created_by": "Codex",
  "status": "PENDING"
}
```

### 2. Windows 중계기

사용자 Windows에서 실행되는 PowerShell 또는 백그라운드 프로세스가 `pending` 폴더를 감시한다.

- `pending` 파일을 하나씩만 처리
- Tailscale 원격 Ollama의 `/api/chat` 또는 `/api/generate` 호출
- 로컬 Ollama로 자동 폴백하지 않음
- `qwen27b:latest`가 없으면 실패 처리
- 결과 원문과 API 메타데이터를 저장

### 3. 결과 폴더

```text
docs\codex_queue\completed\<request-id>\
  request.json
  raw_response.json
  result.md
  response_sha256.txt
  manifest.json
```

실패 시:

```text
docs\codex_queue\failed\<request-id>\
  request.json
  error.txt
  manifest.json
```

### 4. 동시 실행 방지

- `docs\codex_queue\.qwen_remote.lock` 사용
- Antigravity 기존 Hermes 호출과 Codex 요청을 동시에 원격 Qwen에 보내지 않음
- 실행마다 고유 request ID 사용
- 기존 결과 파일을 덮어쓰지 않음

## 절대 금지 사항

- Codex 샌드박스가 Tailscale에 접근한다고 허위 보고하지 말 것
- 로컬 `qwen2.5:7b`를 `qwen27b:latest`로 간주하지 말 것
- 원격 Ollama를 `0.0.0.0`에 공개 바인딩하지 말 것
- 기존 Antigravity 감사 결과를 Codex/Qwen 독립 결과로 재사용하지 말 것
- 사용자가 요청하지 않은 Day 9 감사나 기능 코드 수정을 실행하지 말 것

## 완료 기준

1. 실제 Windows에서 중계기를 실행한다.
2. 최소 테스트 프롬프트 `Reply with exactly REMOTE_OK`를 원격 Qwen 27B에 전송한다.
3. `raw_response.json`에 원격 응답 원문이 저장된다.
4. `manifest.json`에 endpoint, model, request ID, 시작·종료 시각, 성공/실패, SHA-256이 기록된다.
5. Codex가 `completed` 결과 파일을 읽을 수 있다.
6. 로컬 7B 폴백이 발생하지 않았음을 로그로 확인한다.

## 보고 형식

구현 후 다음만 보고한다.

- 수정·추가 파일의 실제 경로
- 실제 테스트 request ID
- 실제 endpoint와 model
- `REMOTE_OK` 수신 여부
- 결과 원문 파일 경로
- 실패 시 정확한 단계와 오류 원문
- 기능 코드 수정 여부: `0건` 또는 실제 목록
