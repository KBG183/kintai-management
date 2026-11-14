### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| C:\dev\kintai\docs\presentation-prototype\evidence.html (2 lines) | qwen27b:latest | 12.8s | 1905 tokens | 427 tokens | 36.9 tokens/s |

| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
| Language Attribute | PASS | The `<html>` tag correctly sets `lang="ja"`. |
| User-Facing Labels & Fallbacks | PASS | All visible text is in Japanese. No Korean characters detected. Fallback messages (e.g., "確認する内容を読み込んでいます。") are appropriate Japanese. |
| Query Parameter Parsing | PASS | The script correctly reads `src`, `title`, `purpose`, `look`, and `slide` using `URLSearchParams`. Default values are provided for missing parameters. |
| Evidence Display Logic | PASS | The viewer implements a dual-mode display: it attempts to fetch and render text-based files (`.md`, `.json`, etc.) as `<pre>` blocks. If the file type is not text-based or the fetch fails, it falls back to an `<iframe>`. A fallback note is displayed if the fetch fails due to browser restrictions. |
| Back Link Behavior | PASS | The back link logic correctly checks `history.length`. If greater than 1, it calls `history.back()`. Otherwise, it redirects to `index.html#slide=N`. The `slide` parameter is correctly parsed and formatted. |
| Raw Source Link | PASS | A raw source link (`#openSource`) is present, correctly bound to the `src` query parameter, and opens in a new tab with `rel="noopener"`. |
| Security & XSS | PASS | The script uses `textContent` for all dynamic string insertions (title, purpose, look, source label, slide number, and fetched text content). This prevents XSS vulnerabilities. The `src` attribute for the iframe is set via property assignment (`frame.src = src`), which is safe. |
| OVERALL | AUDIT_PASSED | All specified checks passed. The HTML is correctly localized, handles query parameters robustly, implements the required display and navigation logic, and avoids common security pitfalls. |