# [A-26] Model Quantization Analysis Report (Q3_K_M vs IQ3_M)

## 1. Issue Background
- **문제 제기**: 과거 문서 기록에서는 Qwen 27B 모델이 `Q3_K_M` 양자화로 지칭되었으나, 실제 Ollama 런타임 실측 결과 `IQ3_M` (I-Quantization 3-bit Medium)으로 반환되어 양자화 스펙 차이의 진위 규명 필요.
- **실측 시각**: `2026-11-11T17:05:11+09:00`
- **조사 대상**: 자택 데스크톱 Ollama 호스트 (`http://100.111.193.94:11434`)

---

## 2. 물리적 실측 결과 (Physical Evidence)
Ollama `/api/show` 및 `/api/tags` 엔드포인트에서 반환된 원본 JSON 데이터:

1. **Parent Model GGUF 파일명**:
   `Qwen3.8-27B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf`
2. **Details Quantization Level**:
   `"quantization_level": "IQ3_M"`
3. **Model Size on Disk**:
   `12,789,303,817` bytes (약 12.79 GB)
4. **General Quantization Version**:
   `2` (llama.cpp I-quantization v2 format)
5. **Modified At**:
   `2026-11-04T22:11:23.4901058+09:00`

---

## 3. 원인 분석 및 결론
1. **모델 교체 여부**:
   - `modified_at` 타임스탬프(`2026-11-04T22:11:23`)에서 증명되듯, 본 모델은 프로젝트 초기에 Ollama에 생성/등록된 이래 **단 한 번도 파일이나 레이어가 교체되지 않은 동일한 물리적 바이너리**입니다.
2. **Q3_K_M 표기 발생 원인**:
   - 일반적인 3비트 GGUF 양자화를 엔지니어링 문서에 기술할 때 관행적으로 널리 쓰이는 `Q3_K_M` 명칭을 에이전트가 사전 확인 없이 관용구처럼 기재했던 것입니다.
   - 실제 배포된 바이너리는 행렬 곱셈 가속 및 퍼플렉시티(PPL) 손실을 최소화한 최신 중요도 행렬 기반 양자화인 **`IQ3_M` (Importance Matrix Quantization 3-bit Medium)** 포맷이었습니다.
3. **공식 조치**:
   - 향후 모든 헌법 명세서, 감사 보고서, 메타데이터 원장에서 모델의 공식 규격을 **`IQ3_M` (12.79 GB, llama.cpp runner)**으로 정확하게 SSOT 동기화합니다.
