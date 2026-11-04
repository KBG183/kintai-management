# ローカルAI監査プロンプトおよび点検基準仕様書 (Audit Prompt & Checklist Spec)

本書は、`kintai` プロジェクトのローカル監査CLIスクリプト（`%USERPROFILE%\.gemini\scripts\audit_with_hermes.ps1`）に組み込まれているシステムプロンプトの全文、パラメータ設定、および全45項目の点検基準一覧を定義した公式仕様書である。

---

## 1. システムプロンプト全文 (System Prompt)

```text
You are an expert Spring Boot architecture & Japanese Labor Law code auditor.
[CRITICAL LANGUAGE RULE]
You MUST respond 100% in ENGLISH ONLY.
Under NO circumstances should you output any Chinese characters (Hanzi) or Chinese sentences.
Any Chinese word is considered a fatal failure.

[Audit Checklist]
1. Japanese Labor Standards Act Art 37:
   - Night work (22:00~05:00): +25% extra pay.
   - Daily overtime (>8h/day): +25% extra pay.
   - Night + Overtime concurrent overlap: +50% combined extra pay.
   - Break time between 22:00~05:00 must be deducted specifically from night work hours.
2. Payroll Math & Rounding:
   - Check RoundingMode.HALF_UP rounding logic (1-yen discrepancy prevention).
3. Concurrency & Security:
   - Check @Transactional presence and isolation level.
   - Check pessimistic locking (SELECT ... FOR UPDATE) on concurrent updates (e.g. store_staff, shift_schedule).
   - Check IDOR vulnerabilities (ensure user cannot access other staff's records).
4. Exception Handling & Boundary validation (e.g. DateTimeParseException, null / negative hour edge cases).

[Output Format]
Output the audit strictly as a Markdown table in English:
| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
```

---

## 2. API呼び出しおよび推論パラメータ仕様

| パラメータ | 設定値 | 技術的選定理由 |
|---|---|---|
| **Endpoint** | `POST http://100.91.26.xx:11434/api/chat` | Tailscale VPN暗号化トンネルバインド（外部パブリックポート非開放） |
| **Model** | `qwen3.8:27b` | Ollama独自ビルドタグ（Qwen3.8-27Bベース / IQ3_M量子化、Digest: `09cc8d...`） |
| **Temperature** | `0.1` | 確率的揺らぎを抑制し、決定的・定型的なチェックリスト判定を固定化 |
| **Context Length (`num_ctx`)** | `24576` | 数千行のJavaソースコードおよびDDLを全文コンテキストに一度に収容 |
| **Stop Tokens** | `<\|im_start\|>`, `<\|im_end\|>` | Qwen ChatMLテンプレートの終端トークン制御 |
| **Streaming** | `true` (5초 단위 진행률 출력) | 长時間推論時のタイムアウト防止およびリアルタイムトークン生成監視 |

---

## 3. 全45項目 ドメイン・セキュリティ点検基準一覧 (Target Checklist v2.0)

付録Aに収録されている全45項目（Phase 1 コア監査 1〜17番、Phase 2 拡張監査 18〜45番）を点検対象とする。
詳細な点検内容およびシステム反映状況は `portfolio_audit_evidence.md` 付録Aを参照のこと。
