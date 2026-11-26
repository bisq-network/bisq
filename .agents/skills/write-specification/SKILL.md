---
name: write-specification
description: "Rules for reading, creating and updating a Bisq 1 domain specification in docs/specifications/: authority over code and tests, location, content, package-local documentation and scope. Use it whenever a change introduces or modifies important domain behavior, invariants, validation rules, protocol rules, DAO consensus rules, security assumptions, state transitions or externally observable semantics, and when specification, implementation and tests disagree."
---

# Reading and Writing Specifications

Specifications describe the intended domain behavior of Bisq 1. They are the authoritative written source for intended behavior and take precedence over behavior inferred only from the current implementation or tests. Start with [the domain index](../../../docs/specifications/README.md).

## Authority and Conflicts

Use this order to determine intended behavior:

1. Explicit developer decisions
2. Domain specifications
3. Current implementation and tests, as evidence of existing behavior

When specifications, implementation and tests disagree:

* Do not resolve the discrepancy by majority, and do not assume the implementation is correct because it exists or a test is intended because it passes.
* Describe the conflicting behaviors and determine whether a developer decision or a specification establishes the intended one.
* If the intended behavior cannot be established confidently, raise the discrepancy with the developer and suggest which of specification, implementation or tests should change and why.
* Do not silently change important domain behavior to make code conform to a specification or to make a test pass.

A specification can itself be wrong or describe a feature whose cost outweighs its use. Question it when it permits a defect; see the `root-cause-analysis` skill.

## Reading Specifications

Before changing domain behavior:

* Read the relevant specifications in `docs/specifications/` and the related documents in [`docs/`](../../../docs/README.md). Consider related domains when a change crosses domain boundaries.
* Check the affected source packages for `*.md` files. They may hold implementation details, history or older rules. If they conflict with `docs/specifications/`, the implementation or tests, surface the conflict rather than silently choosing one.

## Tests Are Not Specifications

Tests may be low quality, generated, outdated, coupled to implementation details or insufficiently reviewed. Treat them as evidence of expected or existing behavior, not as specifications.

* Do not infer a domain requirement only because a test asserts it, and be careful when preserving unusual behavior only because a test expects it.
* If a test enforces behavior that appears suspicious, unnecessarily restrictive or contrary to the specification, raise it with the developer.
* Once the intended behavior is established, update incorrect or obsolete tests.

Tests should verify the specification rather than define it.

## When to Write or Update

Create or update the specification when a change introduces or modifies important domain behavior, invariants, validation rules, protocol rules, security assumptions, state transitions or externally observable semantics. Specifications and code evolve together: a code change that affects specified behavior includes the specification update in the same change set.

If an existing specification covers the behavior, extend it rather than creating an overlapping document.

## Location

`docs/specifications/` is the canonical location, organized by domain: `account/`, `api/`, `application/`, `dao/`, `dispute/`, `network/`, `offer/`, `payment/` and `trade/`. Use domain terminology that matches the codebase, without structural package prefixes such as `bisq`.

Place a new document in its domain directory, or add a domain directory when none fits. Add every new document to the index in `docs/specifications/README.md` with a one-line description of what it covers.

Prefer small, focused files covering one coherent aspect of a domain over large documents covering a whole subsystem.

## Content

Describe **what** the system must do and **why**, independent of how particular classes, methods or packages implement it. Cover, where relevant:

* who acts, the required conditions and the observable outcome
* domain rules and invariants
* important validation rules and state transitions
* exceptional and boundary cases, failure and recovery
* security requirements and trust assumptions
* protocol, DAO consensus and backward-compatibility requirements
* the rationale behind important or non-obvious requirements

Mark disputed behavior, unspecified behavior and known implementation gaps explicitly instead of describing the implementation as if it were intended. Record only limitations whose cause cannot be removed at this time, together with the reason.

## Package-Local Documentation

* `docs/specifications/` describes what the system must do and why.
* Package-local `*.md` files and other documents in `docs/` describe how a particular part of the implementation works.

Domain specifications should not depend on the current package, class or module structure, because domain concepts outlive implementation structures. Implementation documentation references the specification instead of duplicating it.

## Scope

Each specification covers one coherent domain concept, rule set, protocol aspect or behavior. Define each rule in one specification and reference it elsewhere. Do not define the same rule in several files.
