# AGENTS.md

Money Matters: a personal, offline-first Android finance app (Kotlin + Jetpack Compose) that keeps true double-entry books, projects leeway and runway, and tracks stocks, mutual funds and FDs under Indian tax rules.

## Read first
- `docs/plan.html`: product plan, decisions, data model, pages, roadmap
- `design/DESIGN.md`: design system (tokens, typography, components)
- `design/mockups/`: approved screen mockups

## Git workflow (mandatory)
1. Never commit directly to `main`.
2. Start every feature or change on a new branch from an up-to-date `main`: `feature/<short-name>`, `fix/<short-name>` or `docs/<short-name>`.
3. Test thoroughly on the branch before merging: build, lint and all tests must pass (`./gradlew lint test` once the app exists), plus manual checks of the affected screens.
4. Merge into `main` with `git merge --no-ff <branch>`, then re-run the checks on `main`.
5. Delete the merged branch locally (`git branch -d <branch>`) and on the remote if it was pushed.

## Non-negotiable rules
- Every transaction balances (debits = credits). Store money as integer paise, never floats.
- Entries in reconciled or closed months are immutable. Corrections go in as reversals or prior-period adjustments.
- Tax rates, limits and section numbers live in versioned per-tax-year data, never in code.
- Any change to ledger, close or tax logic needs tests that prove these rules still hold.
- UI follows `design/DESIGN.md`: JetBrains Mono throughout, semantic colors only for meaning.
- Keep changes small and focused; update `docs/plan.html` when a decision changes.
