# Delayed payout receiver integrity

## Scope

This specification defines the trust and version requirements for constructing and verifying
Burning Man delayed payout transaction (DPT) receivers. It covers proposal-body authenticity,
address-list negotiation, protected DPT operations, refund-agent verification, and historical
decoding compatibility.

## Authenticated proposal inputs

Every compensation proposal body used to derive a Burning Man candidate must be eligible under the
DAO proposal-validation rules. In particular, its transaction type must be correct and its canonical
body must match the proposal transaction's OP_RETURN commitment. Matching a proposal body to an
issuance by transaction ID alone is insufficient.

The same requirement applies to reimbursement proposal bodies used by Burning Man burn-target
accounting. Append-only P2P admission and a self-consistent storage hash do not authenticate either
proposal body against the DAO transaction.

See [`../dao/proposal-validation.md`](../dao/proposal-validation.md) for the common eligibility
boundary.

## Address-list versions

Bundled address-list resources are immutable historical data. The versions available for historical
lookup and the versions eligible for a new security decision are separate concepts.

The minimum negotiable and enforceable DPT address-list version is `1`. Version `0` represents the
absence of an address-list selection; it is not an address-list resource and must never select a
no-validation policy.

During new trade setup:

- a node advertises only bundled versions at or above the minimum;
- a peer's version list must be non-null, non-empty, sorted, distinct, and positive;
- both peers select the highest eligible common bundled version; and
- absence of an eligible common version aborts trade setup.

Historical versions below the current minimum may remain in a peer's syntactically valid list for
compatibility, but they are ignored during selection and are not advertised by a fixed client.

Raising the minimum requires an explicit coordinated protocol and release decision. Merely bundling
a newer historical resource does not make older versions ineligible.

## Protected operations fail closed

Before reading DAO candidates, every operation that constructs or verifies DPT receivers must
require an address-list version at or above the minimum and present in the local bundled resources.
This includes:

- seller DPT construction;
- buyer verification of prepared and final DPTs; and
- refund-agent reconstruction and verification from a supplied dispute contract.

An obsolete, negative, unknown, or otherwise unsupported version must abort the operation. On
mainnet, an address-list resource for another network must also abort the operation. Non-mainnet
developer modes may explicitly omit filtering when no resource exists for their configured network;
that behavior must not become a mainnet recovery path.

After version validation, candidate addresses and reference shares are filtered using the selected
resource, and every resulting receiver address is checked against its allowlist.

## Historical compatibility

Persisted contracts and process state may decode an absent address-list field as `0`. Decoding and
displaying those records remains tolerant so an old record cannot prevent application startup or
access to unrelated history. Tolerant decoding does not authorize a protected DPT operation: an
attempt to construct or verify receivers from such a record fails closed.

No compatibility exception may rely only on a sender-supplied dispute date, selection height, or
contract scalar. Supporting a legacy version-`0` protocol action would require a separately
specified path authorized by trusted local or on-chain evidence.

## Security rationale

An append-only proposal payload can carry a forged body with a self-consistent storage hash while
reusing a real issuance transaction ID. If raw proposal fields enter the candidate model and version
`0` disables the bundled list, an attacker-controlled name or receiver can be reconstructed as an
expected DPT output. Authenticating proposal bodies removes the earliest invalid input; failing
closed on the version removes the independent downgrade and no-validation path.

These rules affect deterministic trade-protocol output. A release that changes them must be
coordinated with the enforced trading-version policy so fixed and vulnerable peers do not derive
different DPTs during rollout.
