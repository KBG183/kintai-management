# [A-19] Model Identity Verification Report (SSOT Raw Evidence)

## 1. Overview & Fact Finding
- **조사 배경**: 헌법 초기 문서상에 표기되었던 `qwen3.8:27b`와 실제 Ollama 인프라 실행 스크립트(`audit_with_hermes.ps1`)에 기본 설정된 `qwen27b:latest` 간의 표기 불일치 원인 규명.
- **실제 물리적 원본 조회 시각**: `2026-11-11T08:05:11Z` (UTC) / `2026-11-11T17:05:11+09:00` (JST)
- **대상 인프라**: `http://100.111.193.94:11434` (자택 데스크톱 NVIDIA GeForce RTX 5070 Ti, Tailscale Overlay Network)

---

## 2. 불일치 발생 원인 규명 (Root Cause Analysis)
1. **모델의 본질**:
   - Ollama에 등록된 모델의 실제 원본 베이스 파일(`parent_model`)은 **`Qwen3.8-27B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf`**입니다.
   - 베이스 아키텍처 모델명은 **`Qwen/Qwen3.8-27B`** (HauhauCS finetune Aggressive variant, 27.3B)입니다.
2. **태그 불일치 사유**:
   - 사용자가 Ollama 모델을 최초 생성/임포트할 때 로컬 태그 이름을 **`qwen27b:latest`**로 명명하여 등록하였습니다.
   - 반면, 전역 규칙(`user_global`)을 수립할 당시에는 베이스 모델 아키텍처 명칭인 `qwen3.8:27b`를 모델 태그로 잘못 혼동하여 문서에 기재했습니다.
   - 즉, **모델 자체가 변경되거나 교체된 것이 아니며, 동일한 물리적 27.3B 모델 파일에 대해 '베이스 모델 명칭(Qwen3.8-27B)'과 '로컬 Ollama 태그(qwen27b:latest)'가 혼용 표기되었던 것**입니다.
3. **공식 조치**:
   - 혼선을 방지하기 위해 모든 런타임 설정(`audit_config.json`), 실행 스크립트(`audit_with_hermes.ps1`), 헌법 문서의 태그를 실제 Ollama 등록 태그인 **`qwen27b:latest`**로 100% 영구 일원화(SSOT)합니다.

---

## 3. 원본 API 응답 데이터 (Raw Response Evidence)

### ① `/api/tags` 원본 반환값 (Raw JSON)
```json
{
  "models": [
    {
      "name": "qwen27b:latest",
      "model": "qwen27b:latest",
      "modified_at": "2026-11-04T22:11:23.4901058+09:00",
      "size": 12789303817,
      "digest": "23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1",
      "details": {
        "parent_model": "Qwen3.8-27B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf",
        "format": "gguf",
        "family": "qwen35",
        "families": ["qwen35"],
        "parameter_size": "27.3B",
        "quantization_level": "IQ3_M",
        "context_length": 262144,
        "embedding_length": 5120,
        "runner": "llamacpp"
      },
      "capabilities": ["tools", "thinking", "completion"]
    }
  ]
}
```

### ② `/api/show` 세부 메타데이터 원본 (Key Metadata)
- **Full Model Digest**: `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`
- **Parent Model File**: `Qwen3.8-27B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf`
- **General Architecture**: `qwen35`
- **Parameter Count**: `27,320,697,856` (27.3B)
- **Quantization Level**: `IQ3_M` (12.78 GB)
- **Context Length**: `262,144` tokens
- **Ollama Version**: `v0.40.16`

### ③ `/api/show` 1차 호출 실패 원문 및 원인 분석
- **실패 원문 (PowerShell 콘솔)**:
  `Invoke-RestMethod : {"error":"invalid character ':' in string escape code"}`
- **실패 원인**:
  PowerShell 5.1 인라인 명령(`-Command`)에서 따옴표를 중첩하여 `Body '{"name":"qwen27b:latest"}'`를 전달할 때, 윈도우 쉘이 큰따옴표를 이스케이프 해제하면서 콜론(`:`) 문자를 잘못된 이스케이프 코드로 인식하여 발생한 클라이언트 측 파싱 에러였습니다.
- **해결 검증**:
  JSON 문자열을 직접 인라인 결합하지 않고 PowerShell 해시테이블 객체(`@{name='qwen27b:latest'} | ConvertTo-Json`)로 안전하게 직렬화하여 호출한 결과, `Exit Code 0`으로 16,546 바이트의 전체 Modelfile 및 파라미터가 정상 수신됨을 확인했습니다.
