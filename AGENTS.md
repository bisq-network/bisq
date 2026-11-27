# AGENTS.md - Bisq 1

## Initialization
Read ~/.codex/AGENTS.md if present and report whether it was loaded.


## Scope

This file contains agent-specific operating rules.
Project setup, contribution workflow, testing, and domain-specific notes are defined in the developer docs.

## Source of Truth (Developer Docs)

Start with:

- [Developer docs index](docs/README.md)
- [Build](docs/build.md)

Most relevant referenced docs:

- [Contributing](CONTRIBUTING.md)
- [Testing](docs/testing.md)
- [IDEA import](docs/idea-import.md)
- [Local regtest/dev network](Makefile)
- [API overview](docs/api-overview.md)


## Agent skills

Task-specific instructions are in `.agents/skills/<name>/SKILL.md`.
Claude Code discovers the same skills through the `.claude/skills` directory symlink.
On Windows, enable Developer Mode or use an account with permission to create symlinks.
For a new checkout, run `git clone -c core.symlinks=true --recurse-submodules https://github.com/bisq-network/bisq`.
If an existing checkout has a plain file containing `../.agents/skills`, run these commands from the repository root to restore the link.

```sh
git config --local core.symlinks true
git restore -- .claude/skills
```

- `root-cause-analysis` for nontrivial defects and competing explanations
- `write-specification` for domain rules and intended behavior
- `review-feedback` for evaluating reviewer claims
- `bisq-pr-reviewer` for pull request review
- `security-review` for security questions, security-relevant reviews and suspected vulnerabilities
- `canonical-encoding` for byte-level preimage compatibility
- `release-notes` for release documents and the in-app update text
- `gradle-verification` for dependency, plugin and Gradle wrapper changes under dependency verification
- `github-workflows` for GitHub Actions workflow changes and their security rules

## Tech and Runtime Context

- Java (JDK 21)
- Gradle wrapper; main project build files use Groovy DSL, with Kotlin DSL in build-logic and bitcoind subprojects
- JavaFX desktop app, daemon/CLI modules, P2P networking, Bitcoin wallet integration, and DAO components

---

## Agent Behavior

- Do not guess missing requirements
- Do not introduce new dependencies without strong justification
- Prefer existing utilities and established patterns over new implementations
- Keep changes minimal and consistent with surrounding code
- Follow the commit-message guidance in [CONTRIBUTING.md](CONTRIBUTING.md) and use `git commit -s -S` for the DCO sign-off and GPG signature. For nontrivial changes, explain the prior behavior, its consequences, and the reason for the change in the commit body.

### Correctness Over Compliance
- Do not optimize for agreement with the developer
- If a request conflicts with architecture, conventions, or correctness, **point it out explicitly**
- Do not silently work around problems or inconsistencies

### No Hidden Assumptions
- Do not infer unstated requirements
- Do not change behavior beyond the requested scope
- If multiple interpretations are possible, **stop and ask for clarification**

### Consistency Enforcement
- Match the style, structure, and patterns of the surrounding code
- Do not introduce new abstractions, patterns, or naming schemes without clear need
- Avoid unrelated refactoring or “improvements”

### Quality Gate (Self-Check Before Completion)
Ensure that:
- The change is minimal and scoped to the request
- The solution follows existing project conventions
- No unnecessary complexity or abstraction was introduced
- No hidden assumptions were made
- The result would pass a strict code review
- Every claim that a build, test or check passes rests on a run of it in this session, with its output read; say which checks did not run
- No failing test, check or verification was deleted, disabled, weakened or skipped to get a green result

### Failure Handling
- If the task cannot be completed safely or clearly, **do not proceed with a speculative or partial solution**
- Clearly state what is missing and what is required

### Rule of Last Resort
- If the requested change would not pass a strict senior code review, **do not implement it—explain why instead**

When uncertain: **stop and ask**

---

## Authority

Human contributors have final authority.
Agents are assistants, not decision-makers.

---

## License

Bisq is licensed under AGPL-3.0.
