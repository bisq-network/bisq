# AI-Assisted Contributions Policy

This policy covers the use of AI tools (large language models, coding agents and similar tools) for contributions to this repository: code, tests, documentation, specifications, commit messages and pull requests.

AI tools are allowed. This policy makes their use transparent and keeps our review and security standards the same, no matter who or what wrote the code.

The key words "MUST", "MUST NOT", "SHOULD" and "MAY" are to be interpreted as described in [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119).

## 1. Accountability

- The human author is fully responsible for everything they submit, whether AI-assisted or not.
- Authors MUST understand their contribution and MUST be able to explain any line of it in review. Pointing to the AI tool is not an explanation.
- Authors MUST make sure they have the right to submit the contribution under the project's license, as certified by the [Developer Certificate of Origin (DCO)](https://developercertificate.org).

## 2. Disclosure

Non-trivial AI-generated code, tests or documentation MUST be disclosed with an `Assisted-by` trailer in the commit message, one line per tool:

```
Assisted-by: <tool>:<model-id>[:<reasoning-level>]
```

- `<tool>`: the AI tool or agent, e.g. `claude-code`, `codex`
- `<model-id>`: the exact model identifier, e.g. `claude-opus-5-5`
- `<reasoning-level>` (optional): the reasoning or effort level, e.g. `high`

Non-trivial means anything beyond single-line completions or purely mechanical changes such as renames or formatting. When in doubt, disclose.

Use `Assisted-by` instead of `Co-Authored-By`. `Co-Authored-By` is reserved for human co-authors.

Example:

```
Fix fee calculation for partial fills

<description of the change>

Assisted-by: claude-code:claude-opus-5-5:high
Signed-off-by: Jane Doe <jane@example.com>
```

## 3. Review and approval

- Only humans approve. An AI review MUST NOT count as an approval, and AI tools MUST NOT be the sole basis for merging a change.
- AI tools MAY be used to assist reviewers, for example as a pre-review.

## 4. Security-critical code

- Security-critical code MUST get an independent deep review by a human: the author plus at least one other person.
- A deep review covers every changed line in its context, including callers, edge cases and failure modes.
- Security-critical code is defined by the paths listed in [`CODEOWNERS`](/CODEOWNERS), e.g. wallet and key handling, trade protocol, network, cryptography, build and dependencies.

## 5. Signing and sign-off

- AI agents MUST NOT have access to commit-signing keys.
- AI agents MUST NOT add `Signed-off-by` trailers. Only a human can certify the DCO.
- The commit signature and the sign-off are always the human author's.

## 6. Data and tools

- Secrets such as private keys and credentials MUST NOT be shared with AI tools.
- Security-relevant work, such as work on private repositories or undisclosed vulnerabilities, SHOULD only be done with the AI vendors' own tools, such as Claude Code or Codex CLI. Reseller or aggregator platforms may have weaker data policies and could leak sensitive information.

## 7. Enforcement

Maintainers MAY close or defer pull requests that do not follow this policy, or that look like unreviewed AI output, without a detailed review.

## 8. Changes to this policy

This policy will be updated as AI tools and our practices change. Changes are proposed via pull request.

## References

- Linux kernel: [AI Coding Assistants](https://docs.kernel.org/process/coding-assistants.html)
- Fedora: [AI-Assisted Contributions Policy](https://docs.fedoraproject.org/en-US/council/policy/ai-contribution-policy/)
- [Developer Certificate of Origin](https://developercertificate.org)
