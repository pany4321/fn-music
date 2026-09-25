<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

# Release Convention (project rule)

Every batch of code changes MUST end with a version bump and a release:

1. Bump `version.properties` at the repo root: `VERSION_NAME` patch +1 (e.g. `1.2.5` → `1.2.6`), `VERSION_CODE` +1.
2. Commit the bump as `chore: prepare <version> release` together with (or right after) the change commits, then push to `main`.
3. CI (`.github/workflows/android.yml`) builds the signed universal APK and publishes a GitHub Release automatically — but only when the `v<VERSION_NAME>` tag does NOT exist yet. If no new Release appears, the version number was not bumped.
4. Never reuse a version name that already has a Release on GitHub.
