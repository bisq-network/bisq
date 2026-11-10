# Dispute agents named in the contract

## Scope

The contract of a trade names the mediator and the refund agent by their key rings. This
specification defines how a node uses these key rings when a mediation or refund dispute arrives.

## Rules

- The contract of every trade names the key ring of the mediator and of the refund agent, together
  with the contract version that introduced them. Maker and taker refuse a contract without them.
- A node accepts an incoming mediation or refund dispute only if its contract names both key rings,
  and only if the key ring of the dispute's agent is the one that the contract names for this kind
  of dispute. This applies to the agent that receives a new dispute and to the trader that receives
  the dispute the agent created for them.
- A contract without these key rings is refused, whatever the trade date.

## Rationale

The agents of a trade are fixed when the trade is made. The check stops an opener from sending the
dispute of a trade to an agent other than the one the contract names.

## Compatibility

Contracts name the agents from release 1.10.4 on. Until then a contract without them was accepted by
agents in all cases, and by traders for trades from before 1 August 2026. This exception was
removed: nodes before release 1.10.10 cannot trade anymore, and no dispute is expected more than two
months after the trade started.

Contracts without the key rings stay readable, so that stored trades and disputes still load.

## Not covered

Disputes of legacy arbitration, which the node only displays.

The contract is not committed on chain, and the contract signatures of the peers are optional in a
dispute. A trader who controls both sides of a trade can therefore write another contract that
names another agent. Stopping this needs a decision of the maintainers.
