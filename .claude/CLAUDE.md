# Project Rules

## Formatting

- **No ASCII art diagrams.** Use Mermaid (or similar renderable notation) for diagrams and
  flowcharts instead of hand-drawn ASCII boxes and arrows.
- **All fenced code blocks must specify a language.** Use ` ```text ` when no specific language
  applies. Never leave the language identifier empty after the opening triple backticks.
- **No comments at the top of files.** No JSDoc preambles, no block comments, no single-line
  summaries. The file name and exports are the documentation.

## Testing

- **Prefer test parametrization over code duplication.** Use `it.each` / `describe.each` or
  loop-driven test generation instead of copy-pasting nearly identical test cases.

## Attribution

- **Never add "Co-Authored-By: Claude" (or any Claude/AI attribution) to commit messages or PR
  descriptions.** This is handled by settings — do not add it manually.
