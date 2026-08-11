# What the hook does (and deliberately does not do)

Configured in `.claude/settings.json` as a **PostToolUse** hook matching `Write|Edit`.
After Claude writes or edits a file, `.claude/hooks/quick-check.sh` receives the tool
payload on stdin and inspects **only that one file**.

## Behaviour by file type

| File pattern | Check | Blocks? |
|---|---|---|
| `*.sh` | `bash -n` (syntax) | **yes** |
| `*.sh` | executable bit, shebang present, `\|\| true` / `set +e` present | no — warns |
| `maestro/**/*.yaml` | YAML parses (`yaml.safe_load_all`, multi-doc aware) | **yes** |
| `maestro/**/*.yaml` | `maestro check-syntax` — real Maestro validation, no device needed | **yes** |
| `maestro/**/*.yaml` | `appId:` header, `tags:` present, no `sleep:`, no coordinate `point:` | no — warns |
| other `*.yaml` / `*.yml` | YAML parses (covers CI workflows) | **yes** |
| `*.kt` / `*.kts` | brace balance only | no — warns |
| `.claude/agents/*.md`, `.claude/skills/*/SKILL.md` | frontmatter starts at line 1 and has `name:` + `description:` | **yes** |

Everything else is ignored and exits 0 immediately.

## Exit-code contract

- **0** — nothing wrong, or advisory warnings only (printed to stdout for the human).
- **2** — a real defect. The message goes to stderr, which Claude Code feeds back to
  Claude so it can fix the file straight away.

Syntax errors block. Style opinions warn. That split keeps the hook useful without making
it a nag that people learn to route around.

## What it must never do

- **No emulator, no APK install, no UI test, no Gradle build.** A hook runs after *every*
  edit; anything expensive here would make editing unbearable and would train everyone to
  disable it. Real verification is a deliberate, separate step
  (`scripts/verify-results.sh`).
- **No mutation.** It does not format, rewrite, `chmod`, install packages, or touch git. A
  hook that edits files while an agent is editing files causes confusing races and can
  silently discard work.
- **No hiding failures.** It never rewrites a failing check into a pass, and it never
  exits 0 on a syntax error. If a check cannot run because the tool is missing (no
  `python3`, no `maestro`), it skips that specific check rather than pretending it passed.

## Runtime

Sub-second in practice. The heaviest check is `maestro check-syntax`, which is a local
parse with no device involvement. `timeout: 30` in `settings.json` is a backstop, not an
expectation.

## Verifying the hook yourself

```bash
bash -n .claude/hooks/quick-check.sh

# should pass (exit 0)
echo '{"tool_input":{"file_path":"maestro/smoke/01-app-launch.yaml"}}' \
  | .claude/hooks/quick-check.sh ; echo "EXIT=$?"

# should fail (exit 2)
printf 'appId: com.si13.app\nname: broken\n---\n- tapOn: [unclosed\n' > /tmp/bad.yaml
mkdir -p /tmp/maestro && cp /tmp/bad.yaml /tmp/maestro/bad.yaml
echo '{"tool_input":{"file_path":"/tmp/maestro/bad.yaml"}}' \
  | .claude/hooks/quick-check.sh ; echo "EXIT=$?"
```
