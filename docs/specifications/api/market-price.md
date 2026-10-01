# API market price

## Scope

This specification covers the market price of a currency that the API returns (`GetMarketPrice`),
and the price feed that it reads.

The price feed requests the prices of all currencies from a price provider, and keeps for each
currency the last price with the time at which the provider calculated it. If a request fails, the
price feed selects another provider. The desktop application, the daemon and the REST node start the
price feed during their startup and repeat the request about every minute while they run. For a
currency that the providers do not deliver, the node can also hold a price derived from its trade
statistics.

## Rules

- A market price request is answered from the prices that the price feed holds. It does not request
  prices and does not wait for a price provider.
- The request returns a price only if a price provider delivered it and the price is less than
  30 minutes old. Otherwise the request fails at once with the status `UNAVAILABLE`, and the client
  can retry later.
- A price derived from trade statistics is not returned.
- A fiat price is rounded to 4 decimal places, a crypto currency price to 8.
- A request does not change the state of the price feed, for example the currency selected for the
  user interface. Requests that run at the same time do not affect each other.

A request to a price provider goes over Tor and can fail or time out, so an API call must not wait
for it. The price feed is shared by all users of the node, so a request must not change it for the
others.

## Compatibility

No message, persisted data or API definition changes.

Before, each request asked a price provider and waited for its answer. If that request failed or
did not deliver a price from a provider for the currency, the call was not answered. A call is now
answered at once, with the cached price or with `UNAVAILABLE`. If the node held no price at all,
the request failed with `UNKNOWN`, and it now fails with `UNAVAILABLE` as well.

While the price providers answer, the returned price can be up to about one minute older than a
price requested for the call. Before, the daemon requested prices once at its start, and repeated
the requests only after the first market price request.

## Not covered

The price of an offer that follows the market price is computed from the same prices with the same
age limit. This specification does not change it.
