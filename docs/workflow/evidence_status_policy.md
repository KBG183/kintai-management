# Evidence Status Policy

This policy defines the minimum evidence required for a task status. A model
opinion alone cannot promote an item to `VERIFIED`.

## Status definitions

| Status | Meaning | Minimum evidence |
|---|---|---|
| `IMPLEMENTED` | Code or document work exists | Target files and scope recorded |
| `REVIEWED` | A reviewer inspected the material | Review report and reviewed-file hash |
| `VERIFIED` | The stated requirement was physically demonstrated | Exit code 0, relevant execution log, JUnit XML or equivalent result, requirement/RTM mapping, and matching environment |
| `PARTIALLY_VERIFIED` | Only part of the scope or environments was demonstrated | Verified subset plus explicit unverified subset |
| `BLOCKED` | Verification could not run because of an environment or dependency constraint | Reproduction of the blocker and exact affected scope |
| `FAILED` | The required check ran and did not pass | Original failure log and affected scope |

## Promotion rules

1. A Qwen/Hermes `PASS` is reviewer evidence, not execution evidence.
2. `VERIFIED` requires a matching test log and result artifact. A prose report
   without those artifacts remains `REVIEWED`.
3. Evidence from MySQL 26.7 must not be used to mark a MySQL 8.4 requirement
   as verified. Environments are separate dimensions.
4. Missing, empty, or stale evidence is recorded as `UNVERIFIED`; it is not
   silently treated as `PASS`.
5. If one required environment passes and another is blocked, the overall
   result is `PARTIALLY_VERIFIED` and the blocked environment remains explicit.

## Required session record

Each work cycle records its operator, task/day, requirement IDs, files, command,
environment, logs, result artifacts, hashes, and unresolved items in a copy of
`active_session.template.json`.
