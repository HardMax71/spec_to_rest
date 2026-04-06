# Project Rules

## Formatting

- **No ASCII art diagrams.** Use Mermaid (or similar renderable notation) for diagrams and
  flowcharts instead of hand-drawn ASCII boxes and arrows.
- **All fenced code blocks must specify a language.** Use ` ```text ` when no specific language
  applies. Never leave the language identifier empty after the opening triple backticks.
- **No large block comments at the top of files.** A single-line summary is fine; multi-line JSDoc
  preambles or ASCII banners are not. Let the code speak for itself.

## Testing

- **Prefer test parametrization over code duplication.** Use `it.each` / `describe.each` or
  loop-driven test generation instead of copy-pasting nearly identical test cases.

## Attribution

- **Never add "Co-Authored-By: Claude" (or any Claude/AI attribution) to commit messages or PR
  descriptions.** This is handled by settings — do not add it manually.
