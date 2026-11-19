# BSQ swap request admission

## Scope

This specification covers which BSQ swap requests the maker accepts for one of its offers: the
buyer's request (`BuyersBsqSwapRequest`), which a taker sends who buys BTC, and the seller's request
(`SellersBsqSwapRequest`), which a taker sends who sells BTC. What the traders verify about each
other's inputs and signatures later in the swap is specified in
[`bsq-swap-seller-inputs.md`](bsq-swap-seller-inputs.md) and
[`bsq-swap-buyer-signatures.md`](bsq-swap-buyer-signatures.md). Take offer requests of the Bisq v1
trade protocol are specified in [`take-offer-request-admission.md`](take-offer-request-admission.md).

## Rules

1. The maker accepts a BSQ swap request only for a BSQ swap offer. It refuses a request for an offer
   of the Bisq v1 trade protocol, whatever its currency, price and payment method.
2. The type of the request must fit the direction of the offer: a buyer's request only for a sell
   offer, a seller's request only for a buy offer.
3. The maker checks rules 1 and 2 together with its other checks of the request, before it reserves
   the offer and before it starts a trade. For a refused request the maker starts no trade, leaves
   the offer available and sends no reply.

## Rationale

A BSQ swap trade takes the price of the offer as the price of BSQ in BTC and computes the BSQ amount
of the swap from it. The maker's node runs the swap without a confirmation of the user. It takes the
BTC from the available funds of its wallet, not from funds reserved for the offer, and signs its
inputs. Without rule 1 a taker could take a fiat or altcoin offer with a fixed price with a BSQ swap
request. The maker would then pay BTC for BSQ, or BSQ for BTC, at a price that was set for another
currency. For an altcoin that is worth much more than BSQ, for example XMR, the maker of a sell
offer would receive a small fraction of the value of its BTC. A market based offer stores a price of
zero, so the BSQ amount of such a swap stays below the dust limit and the swap fails, but rule 1
does not rely on that.

Without rule 2 the maker detected a request of the wrong type only after it had reserved the offer.
Such a request kept the offer away from other takers until the reservation ended after 60 seconds.

## Compatibility

No message and no persisted data change. Takers of all versions send a BSQ swap request only for a
BSQ swap offer, and the type of their request follows from the direction of the offer, so the maker
refuses no request of an unmodified taker. Makers of earlier versions do not apply rules 1 and 2.
Their fiat and altcoin offers with a fixed price stay exposed until they update.

## Not covered

- A take offer request of the Bisq v1 trade protocol for a BSQ swap offer. The maker does not refuse
  it with its checks of the request. The trade fails in the maker's first step, before the maker
  builds or signs a transaction, for example because a BSQ swap offer has no security deposits. The
  failed trade stays in the maker's pending trades.
- A BSQ swap request that the maker accepts but that fails later in the swap, for example because of
  invalid BSQ inputs, keeps the offer reserved until the reservation ends.
- The maker does not check the filter flag that disables BSQ swaps when it receives a BSQ swap
  request. The flag hides BSQ swap offers and stops the creation and the taking of BSQ swap offers,
  but it does not stop the maker's side of a swap.
