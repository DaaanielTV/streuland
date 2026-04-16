# Contributing to Streuland Plot Plugin

Thanks for contributing to Streuland Plot Plugin. We use a lightweight issue + pull request workflow.

## 1) Before You Start

- Search existing issues and pull requests first.
- Open an issue for non-trivial changes before implementing.
- Keep proposals focused: problem, approach, risks, and test plan.

## 2) Branching

Use descriptive branch names:

- `feat/<short-description>`
- `fix/<short-description>`
- `docs/<short-description>`
- `chore/<short-description>`

## 3) Development Checklist

1. Build and test locally:

   ```bash
   mvn clean verify
   ```

2. Update docs for behavior, API, or configuration changes.
3. Avoid unrelated refactors in the same PR.
4. Keep commits clear and reviewable.

## 4) Pull Request Requirements

Every PR should include:

- Summary of what changed and why
- Linked issue (`Fixes #...` or `Refs #...`)
- Test evidence (commands + result)
- Notes for breaking changes or migrations

## 5) Code Style and Quality

- Follow existing project conventions and package structure.
- Prefer small, focused changes.
- Do not commit generated outputs (`target/`, logs, cache files, IDE artifacts).

## 6) Reporting Security Issues

Please do **not** open public issues for sensitive vulnerabilities. Instead, contact maintainers privately and include:

- affected component
- reproduction steps
- potential impact
- suggested mitigation (if available)

## 7) Community Expectations

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
