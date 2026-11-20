# Script types from Bitcoin Core

## Scope

A full DAO node reads the Bitcoin chain through the RPC interface of Bitcoin Core. For each output of
each transaction of a block, Bitcoin Core reports a script type, for example `witness_v0_keyhash`. The
node maps it to a script type of the DAO state, and the script type of a BSQ output is part of the DAO
state hash. The Burning Man accounting node maps the type in the same way. This specification defines
the mapping.

## Rules

- A type that the release knows maps to the script type of the same name: `nonstandard`, `pubkey`,
  `pubkeyhash`, `scripthash`, `multisig`, `nulldata`, `witness_v0_keyhash`, `witness_v0_scripthash`,
  `witness_v1_taproot` and `witness_unknown`.
- The type `anchor`, which Bitcoin Core 28 and later report for pay-to-anchor outputs, maps to
  `witness_unknown`.
- Any other type, also a missing one, does not stop the parsing of the block. The node maps it by the
  script bytes of the output:
  - A witness program of version 1 to 16 maps to `witness_unknown`. A witness program is a version
    opcode (`OP_0`, or `OP_1` to `OP_16`) followed by one push of 2 to 40 bytes that ends the script.
  - Every other script maps to `nonstandard`. This includes a witness program of version 0, a missing
    script and a script that is not valid hex.
- No type maps to `undefined`. It is not a type of Bitcoin Core, and it has no protobuf value, so the
  DAO state could not be persisted with it.
- The node logs a warning the first time it maps an unknown type.

## Rationale

Bitcoin Core adds types for new scripts, for example `witness_v1_taproot` in version 22 and `anchor` in
version 28. Before this rule, a full node stopped at the first block with an unknown type: it reported
an error and parsed neither this block nor any later one, also after a restart. Any Bitcoin
transaction with such an output stopped the node, not only a BSQ transaction.

Full nodes can run different Bitcoin Core versions, and the script type of a BSQ output is part of the
DAO state hash. A Bitcoin Core version that does not know a script reports a witness program of
version 1 to 16 as `witness_unknown` and any other script as `nonstandard`. The mapping uses the same
rule. So for a new script, a full node with a newer Bitcoin Core version stores the type that a full
node with an older version receives from its Bitcoin Core. The type `anchor` follows the same rule:
older versions report pay-to-anchor outputs as `witness_unknown`.

## Compatibility

The rule changes the result only for types that stopped the parsing before. For every block that a
full node could parse before, the DAO state and its hash stay the same, so no activation height is
needed. A full node of an earlier release still stops at a block with an unknown type.

## Known gaps

- If a later Bitcoin Core version renames the type of a known script, the rule maps the new name by
  the script bytes. The result can differ from the type that an older version reports.
- The DAO state hash still contains the script type and the `asm` text that Bitcoin Core reports, so
  the hash still depends on how Bitcoin Core describes a script. Only a hash of the script bytes
  removes this dependency. That changes the hash chain and needs an activation height.
