# Refund claimant authentication

## Purpose

A refund-wallet payout must not be authorized merely because a claimant can reproduce public
deposit and delayed-payout transaction data. The claimant must prove control of an escrow private
key committed by deposit output `0` and therefore prove participation in that escrow.

This proof is created when a refund dispute is opened. It does not change trade negotiation,
deposit inputs, wallet address allocation or any Bitcoin transaction format.

Claim creation must leave the dispute's existing signature and signed opening date unchanged if
key lookup, unlocking, signing or signature self-verification fails. Attach the new signature and
its matching opening date only after successful creation and verification. A failed attempt must
not create a partially signed record or corrupt a previously retained proof.

## Claim proof

The trader opening a refund dispute must sign the claim with the private escrow key for the
trader's buyer or seller role. Bitcoin message signing must be used so the proof cannot be confused
with a Bitcoin transaction signature made by the same key.

The signed claim must use the domain `bisq-refund-claim-v1` and a deterministic, canonical encoding
of:

- the trade identifier and contract hash;
- hashes of both identity-key rings excluded from the contract JSON;
- both escrow public keys excluded from the contract JSON;
- the deposit and delayed payout transaction identifiers;
- the claimant's buyer or seller role and the claim's dispute-opening date;
- the refund agent identity-key ring;
- the Burning Man selection height, trade transaction fee and legacy donation address, when
  present.

The contract hash already commits the payout addresses and the other contract terms. Domain
separation and the complete field binding prevent a valid proof from being repurposed for another
trade, transaction chain, role, agent or dispute opening.

### Version-one signed format

The selected format is the existing canonical binary statement, encoded as lowercase hexadecimal
and signed as a Bitcoin signed message. It is not the alternative newline-separated
`Bisq refund claim v1` format. The formats are not interchangeable even though both proposals use
the same serialized signature field.

The outer canonical message contains the nested claim subject at field 1, the buyer-role boolean
at field 2, and the signed opening date in milliseconds since the Unix epoch at field 3.
The subject fields are:

| Field | Type | Value |
| --- | --- | --- |
| 1 | String | `bisq-refund-claim-v1` |
| 2 | String | Trade identifier |
| 3 | Bytes | Contract hash |
| 4 | Bytes | SHA-256 of canonical maker identity-key ring |
| 5 | Bytes | SHA-256 of canonical taker identity-key ring |
| 6 | Bytes | Maker escrow public key |
| 7 | Bytes | Taker escrow public key |
| 8 | String | Deposit transaction identifier |
| 9 | String | Delayed payout transaction identifier |
| 10 | Bytes | SHA-256 of canonical refund-agent identity-key ring |
| 11 | Integer, 32 bits | Burning Man selection height |
| 12 | Integer, 64 bits | Trade transaction fee in satoshis |
| 13 | String | Legacy donation address, when present |

Fields use canonical protobuf-style tags and ascending field order: integers and booleans use
varints; strings use UTF-8; strings, bytes and nested messages are length-delimited.
Zero integers, false booleans, empty strings and absent optional values are omitted. Thus the seller
role omits outer field 2. Each canonical identity-key ring contains the signature public-key bytes
at field 1 and encryption public-key bytes at field 2. The claim-subject hash used to bind cached
validation is SHA-256 of the nested subject bytes, not of the outer message or its hexadecimal text.

The serialized dispute stores the signature in field 33 and signed date in field 34. Changing
these signed bytes requires an explicit version and compatibility decision, not silently redefining
version one. Fixed buyer and seller encoding vectors are retained in the refund claim test resources.

## Admission

An inbound new refund dispute must carry a claim signature. Before storing it, the refund agent
must verify all of the following:

1. The ordinary dispute identity and contract validations succeed.
2. The serialized deposit transaction binds both contract escrow public keys to deposit output `0`.
3. The claim verifies with the contract escrow public key for the declared opener role.
4. The declared maker or taker role and the opener identity-key ring agree with that contract role.
5. The signed claim-opening date equals the opening date on the incoming dispute.

A signature made by the other trader's escrow key does not authenticate the declared opener. A
missing, malformed or non-verifying signature fails admission.

Once the request's ordinary identity, contract and sender-address checks succeed, the agent must
return a negative acknowledgment for a rejected claim when the opening chat message is bound to
that request's support type, trade, trader and opener address. Missing-proof feedback must explain
that the trader needs to upgrade and submit the refund request again. Do not acknowledge an
unauthenticated sender or route a reply through an unrelated embedded chat address. Transport
delivery alone does not mean the agent accepted the request. A client reopening an existing ticket
must retain the new opening chat message on its stored row so delivery and acknowledgment updates
remain visible and can be correlated by message identifier.
Rejection feedback takes display priority over an earlier mailbox-delivery status; leaving that
historical status set must not conceal the agent's error.

Claim authentication applies on every network, including the local regression-test network. A close
attempt must check it before requesting explorer evidence, as well as at the later authorization
boundaries. Explorer unavailability does not waive authentication. The separately specified,
regtest-only development bypass for transaction evidence does not bypass this claim check.

The escrow public key is taken from the validated contract, not from delayed-payout witness bytes.
Witness bytes are not committed by the transaction identifier and therefore must not become
explorer-trusted claimant identity data.

## Mirrored and independently opened dispute rows

The refund agent creates a peer-facing row whose row-level opener flags and row creation date describe the other
trader. That row must preserve the original claim signature and separately stored signed opening date. Close-time
verification determines the claim signer from the signed buyer or seller role and corresponding escrow key; it must
not reinterpret the proof using the mirrored row's flags.

If both traders independently open disputes, their proofs may jointly authenticate both roles only
when every claim-subject field other than role and opening date is identical. A proof from a row with
a different contract, transaction chain, agent or validation input cannot authorize the selected
row.

A malformed selected claim subject must fail with its validation error rather than being treated
as an absent claimant proof. An invalid optional candidate row may be ignored. A well-formed
selected row need not carry its own valid proof if an independently verified matching row proves
the required recipient role.

When a valid newly opened dispute matches a row already stored for the same trader and claim subject,
the stored row must retain the new proof and its signed date. This permits an existing ticket created
before claimant proofs were available to acquire one after the trader upgrades and opens it again.

A changed proof must produce a local system-chat notice on the agent's retained ticket, without
reopening a closed case or changing its result or paid state. Re-delivery of the identical proof
and signed date must not duplicate the notice. The notice is informational, not payout approval;
close-time verification still applies.

Replay validation must evaluate such an update as replacing the matching stored row, not adding a
third ticket merely because the request has a new identifier. All other stored rows remain in the
replay check. A different subject cannot replace that row or acquire its proof, result or paid state.
The incoming claimant identity must also equal the stored row's trader identity; the numeric trader
identifier alone is not authority to replace a row.

## Payout and result authorization

The claim proof must be verified again as part of close-time transaction validation, immediately
before a direct payout, and immediately before the refund agent signs a dispute result. A cached
chain-validation result does not replace this re-verification.

For the normal case in which exactly one trader receives a positive refund payout, that recipient
must have supplied a valid claim proof for the same funding evidence. An opener cannot authorize a
sole payout to the other role's unverified address.

If the original opener loses and only the other trader should receive a refund, the winning trader
must submit their own signed refund request for the same claim subject. The automatically mirrored
ticket is not that trader's proof. An updated client can send the request from the pending trade's
visible **Send refund claim proof** action in both the self-requested and peer-requested refund states,
then choose **Open dispute again** if a ticket already exists. This action uses the ordinary signed
refund-request flow, including its wallet, deposit-confirmation and lock-time checks. Merely reopening a closed
ticket in the support view changes local state and does not send a new claim.

The agent must keep the payout blocked until that matching proof arrives. If the winner is
unavailable or their locally retained contract differs from the original claim subject, the case
requires investigation; the losing opener cannot authorize the winner's address. Do not add a
nominal second payout merely to evade the single-recipient requirement.

A payout with positive amounts for both traders is exceptional. It may proceed with at least one
valid opener proof, but the software must warn the refund agent to verify both payout addresses
directly with the traders. This manual exception deliberately does not introduce trade-time escrow
signatures or another dispute protocol. If the agent cannot establish the addresses, no payout is
authorized.

Manual verification must independently establish the actual counterparties as well as their
addresses. Contacting only the alleged peer identity supplied by the opener is circular: a dishonest
opener can control that identity. If independent verification is unavailable, this exception cannot
be used safely.

The agent must explicitly confirm this verification before closing a two-recipient case, including
when the peer row is already closed or no wallet payout is being made. Approval is local to the
current dialog, claim subject and buyer/seller amounts. Changed evidence or allocation requires
fresh approval. Both payout and result-signing authorization must enforce this requirement;
displaying a warning only in the wallet transaction confirmation is insufficient.

For both split-payout and legacy confirmations, an observed mismatch in the dispute row, claim subject,
allocation or legacy eligibility must discard the saved approval. A malformed subject must also discard it.
Restoring the previously approved values must not revive that confirmation. Check for such changes even
when the current allocation no longer requires manual verification, and require a fresh explicit confirmation
before a later attempt can use the manual exception again.

Closing without a payout still requires a valid claimant before the agent signs a refund result,
except for the temporary legacy-record grace period below.

## Temporary grace period for existing disputes

Until **1 November 2026 at 00:00 UTC**, an already stored refund dispute with no claim signature
and no signed claim-opening date may be closed through manual verification. This exception covers
legacy records retained by the refund agent when upgrading. New unsigned requests remain rejected
at admission; an incoming opening date or matching ticket identifier does not establish eligibility.
Restored local records are treated as trusted local history, not new network admission.

The agent must see a warning and explicitly confirm that they have manually verified participation
in the real trade and independently verified every payout recipient and address. Cancelling the
warning stops closing. The confirmation applies only to that dialog's dispute, claim subject and
buyer/seller amounts; changed evidence or allocation requires fresh confirmation.

This exception permits the missing proof and its recipient-role requirement to be replaced by
manual verification. It does not waive transaction validation, payout amount limits or receipt
consumption. The existing regression-test transaction-evidence exception remains separate.
Malformed/nonempty signatures and records with a signed date but a missing signature do not qualify.

The grace period uses the agent's current clock, not the dispute's opening date. At and after the
cutoff, all claim checks apply normally, including for old disputes and dialogs confirmed before
the cutoff. Eligibility and manual approval are checked again before payout and result signing
on every network. No permanent waiver is persisted.

The grace-period code, confirmation state, popup and related tests can be deleted in releases
after 1 November 2026. Relevant production code is marked with removal comments.

## Compatibility and residual risks

The claim signature is an optional serialized field for wire compatibility with older readers, but
an updated refund agent fails closed when a new refund dispute does not contain it. Existing and
in-flight trades can create the proof after a client upgrade because the signing key is the escrow
key already retained by the wallet. No trade-time negotiation or on-chain migration is required.

Both the refund agent and submitting trader must upgrade. Older agents ignore the new fields and
do not enforce this protection; updated agents reject unsigned old-client requests. Locally retained
older tickets acquire a proof only when a valid matching request is received, not merely by being
loaded or reopened locally. During the temporary grace period, eligible stored tickets may instead
be closed after the manual verification described above. Deployment must use the selected signature format consistently. If an
incompatible experimental format has already been deployed, explicit migration is required before
interoperation; no automatic format fallback is defined here.

The signed date binds a statement to an opening event, but is neither an agent challenge nor proof
of current liveness. Receipt-consumption checks remain responsible for preventing repeat payments.

This proof establishes that the claimant participated in the escrow. It does not prove that both
traders accepted all identity data excluded from legacy contract hashes. The single-recipient rule
prevents that residual gap from redirecting the common payout; two-recipient payouts rely on the
explicit agent verification above.

A real self-trader controls the actual escrow keys and can therefore produce valid proofs for a
self-funded trade. Cryptography cannot distinguish such a trade from one between independent
people. The security deposits and fees, refund-agent judgment, Burning Man concentration limits and
public DAO reimbursement review remain the controls for that separate economic risk. A full refund
to both sides deserves particularly strong, independently checkable evidence.
