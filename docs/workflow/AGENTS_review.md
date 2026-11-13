# AGENTS.md Review and Remediation Plan

## Scope and preservation rule

- Reviewed source: `C:\dev\kintai\AGENTS.md`
- This document does not modify or replace the source.
- Facts below come from the reviewed files or build configuration. Recommendations
  are explicitly labeled as recommendations.

## Confirmed facts

| ID | Finding | Evidence | Impact |
|---|---|---|---|
| F-01 | The project toolchain is Java 17, while AGENTS.md states Java 21. | `build.gradle` sets `JavaLanguageVersion.of(17)`; AGENTS.md Team Role Architecture | An operator can write or review against the wrong runtime baseline. |
| F-02 | Hermes execution paths conflict. | AGENTS.md names `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1`; active project script is `C:\dev\kintai\scripts\audit_with_hermes.ps1`. | Instructions can call an obsolete or different script. |
| F-03 | Hermes host values conflict. | AGENTS.md contains `100.111.193.94`; the current project audit script default contains `100.91.26.37`. | Audit requests can be sent to the wrong host or fail. The current authoritative endpoint has not been re-verified in this review. |
| F-04 | Model policy is internally inconsistent. | AGENTS.md preserves `qwen2.5:7b` fallback rules; current operating policy says the 27B Antigravity/Hermes route is primary and Codex queue routing is not the standard route. | A lower-capacity model or retired workflow can be used unintentionally. |
| F-05 | The document contains mandatory daily full-audit, pre-audit, re-audit, and three-step source-wide audit requirements. | AGENTS.md sections 6, 11, 14, and 17. | Small changes can trigger disproportionate work, delay the Day-based plan, and encourage checkbox evidence rather than risk-based evidence. |
| F-06 | Status definitions are duplicated. | AGENTS.md section 20, `operating_policy_v2.md`, `day_task_template.md`, and `evidence_status_policy.md`. | Different agents can apply different meanings to `VERIFIED`, `BLOCKED`, and `PARTIALLY_VERIFIED`. |
| F-07 | Codex routing instructions conflict. | AGENTS.md focuses on a three-tier Antigravity/Hermes/user model; `shared_operator_workflow.md` still describes Codex queue worker operation. | Codex can appear mandatory or automatic despite the current user-selected optional role. |
| F-08 | The roadmap is embedded inside the top-level instruction document. | AGENTS.md Daily 1-Sprint Incremental Roadmap includes mutable Day 1 through Day 13 status and targets. | The constitutional rules become stale whenever the current Day changes. |
| F-09 | Several rules use absolute completion claims and hard percentages. | Examples include repeated `100%`, fixed performance claims, and unconditional safety language. | The wording conflicts with evidence-based status reporting and can lead to overstatement. |
| F-10 | The file is valid UTF-8 without a BOM. | Strict UTF-8 decode succeeded for `AGENTS.md`. | The garbled PowerShell display observed during review is a console/rendering issue, not proof that the file itself is corrupted. |

## Recommended target structure

`AGENTS.md` should become a short, stable project charter. Detailed and mutable
procedures stay in `docs/workflow`.

| Layer | File | Responsibility |
|---|---|---|
| 1. Highest project rule | `AGENTS.md` | Scope, authority order, roles, evidence principle, explicit-write and external-action authorization |
| 2. Operating policy | `docs/workflow/operating_policy_v2.md` | Standard work cycle and optional Codex role |
| 3. Evidence policy | `docs/workflow/evidence_status_policy.md` | Single definitions for status promotion and environment separation |
| 4. Active plan | `docs/workflow/day_task_template.md` and current Day record | Mutable Day scope, requirement IDs, and completion criteria |
| 5. Qwen audit contract | `docs/workflow/qwen_audit_input_checklist.md`, `qwen_audit_manifest.template.json`, validator script | Input completeness and independent audit evidence |
| 6. Paths and scripts | `docs/workflow/project_paths.md` and `scripts/` | Current canonical locations and executable behavior |

## Proposed AGENTS.md rules

1. User instructions override every project file.
2. `AGENTS.md` resolves only cross-document conflicts; it must not duplicate
   detailed checklists, Day status, benchmark numbers, or command recipes.
3. Antigravity implements and runs normal development checks. Qwen 27B is an
   independent reviewer. The user is final approver. Codex acts only when the
   user explicitly requests its work.
4. A Qwen `PASS` is review evidence. `VERIFIED` requires the evidence policy's
   execution artifacts and matching environment.
5. Environment-specific results remain separate. A local MySQL result does not
   prove MySQL 8.4 behavior.
6. Audit depth is risk-based: required inputs and relevant test evidence are
   mandatory; duplicate full-project audits are not automatically required for
   every low-risk edit.
7. Model endpoint, model tag, script path, and benchmark values belong in a
   versioned configuration or an evidence record, not a top-level rule file.
8. Any commit, push, deployment, deletion, or external action requires the
   user's explicit request. A workflow rule must not infer that authorization.
9. The top-level document must avoid unsupported absolute claims such as
   `100% safe`, `complete`, or `verified` without the underlying evidence.

## Risks of the recommended structure and controls

| Risk introduced or exposed | Why it matters | Control |
|---|---|---|
| More files to consult | An operator may read only AGENTS.md and miss a procedure. | Keep AGENTS.md under a compact size limit and include a canonical-document table with links. |
| Policy drift between layers | A status rule can be changed in one file only. | Declare `evidence_status_policy.md` as the only status-definition source; remove duplicated definitions elsewhere or replace them with links. |
| Stale endpoint/configuration | Network address and model tag can change. | Put runtime values in one configuration file and record the value actually used in each audit manifest. Do not declare a host as permanently valid. |
| Excessive process overhead | Mandatory audits for every text edit slow Day work. | Use a risk matrix: high-risk code and security/payroll changes require independent audit; document-only and low-risk changes require proportional checks. |
| Optional Codex role becomes ambiguous | Another agent may invoke Codex or a queue worker automatically. | State once in AGENTS.md and operating policy: Codex is user-requested only; no automatic cross-agent routing. |
| Migration breaks an existing automation | Deleting paths or renaming instructions can invalidate scripts. | Preserve the original file until the replacement passes link/path checks; change references in a separate, reviewable pass. |

## Safe execution sequence

1. Freeze `AGENTS.md` as the source snapshot and keep this review beside it.
2. Create a compact replacement draft without changing runtime scripts.
3. Reconcile model host, model tag, and canonical Hermes script location using
   a direct read-only environment check.
4. Move mutable Day roadmap and detailed procedures out of the replacement;
   leave links to their canonical workflow files.
5. Make `evidence_status_policy.md` the single source for status definitions.
6. Search all active documents for superseded paths, model tags, and old
   workflow assertions; update only confirmed stale references.
7. Validate UTF-8, Markdown links, JSON templates, and PowerShell parser
   results. Do not run remote model calls merely to validate documentation.
8. Present the replacement and a migration diff for user review before
   replacing `AGENTS.md`.

## Completion criteria for the next phase

- No Java version, script path, model policy, endpoint, role, or status rule
  contradicts its canonical source.
- The replacement contains stable rules only and points to mutable procedures.
- All active workflow documents name a single authority for status definitions.
- The original `AGENTS.md` remains recoverable until the user accepts the
  replacement.
