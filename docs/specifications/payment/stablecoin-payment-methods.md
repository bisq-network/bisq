# Stablecoin payment methods

## Scope

This specification defines how a stablecoin pays for BTC in a Bisq v1 trade: which coins and
networks are listed, what an offer with a stablecoin payment method means, what the account data
is, and which rules of the fiat payment methods apply. It does not define the altcoin markets of
stablecoins (tickers such as `USDT-E`), which follow the rules of altcoin markets.

## Terms

- A **rail** is one coin on one network, for example USDT on Tron.
- The **peg currency** of a coin is the currency whose value the coin follows, for example USD.
- The **token contract** of a rail is the contract that holds the balances of the coin on the
  network (on Solana the mint).

## Rules

1. A stablecoin payment method is a payment method of the peg currency of its coin. An offer with
   it is an offer of the market of that currency: the price is the peg currency per BTC, as for a
   bank transfer in that currency.
2. The volume of a trade is paid with the same number of units of the coin on the network of the
   rail: 1000 USD are paid with 1000 USDT. The market price of the coin is not considered. Neither
   the price of an offer nor the volume of a trade changes when the coin trades above or below its
   peg currency.
3. There is one payment method per rail. Its ID is the name of the rail: the code of the coin in
   upper case, an underscore and the token standard, for example `USDT_TRC20`. A released ID is
   never renamed, reused or removed, as it is stored in accounts, offers, contracts and trade
   statistics.
4. Only the token with the token contract of the rail is the coin of the rail. A token with the
   same name or code but another token contract is not a payment.
5. The account data is the address of the account on the network of the rail. It is stored without
   white space around it and checked in the address format of the network:
   - Ethereum and BNB Smart Chain: the prefix `0x` and 40 hex digits. If the address has letters in
     both cases, they must match its checksum (EIP-55). The address is stored with the letters in
     the case of its checksum;
   - Tron: Base58Check of the version byte 0x41 and a 20 byte account hash;
   - Solana: Base58 of a 32 byte public key.
6. An account matches only the offers of its own rail. The same coin on another network is another
   payment method.
7. A transfer on a blockchain cannot be charged back. The trade limit is the highest limit class,
   as for altcoins, and it does not depend on the age or the signing of the account.
8. The trade period is 24 hours.
9. All other rules are those of a fiat payment method:
   - the account has an account age witness, and the seller validates the account of the buyer
     before the deposit is published (see
     [`fiat-buyer-payment-account-validation.md`](../trade/fiat-buyer-payment-account-validation.md));
   - the lock time of the delayed payout transaction is that of the fiat payment methods;
   - the volume is rounded to whole units of the peg currency;
   - a trade statistic has the peg currency as currency and the ID of the rail as payment method.
10. The BTC buyer pays the network fee, and the BTC seller receives the full volume. A transfer on
    another network or of another token is not a payment, and the sender bears the loss. A transfer
    from any address is a payment: the address of the buyer's own account need not be the sender.
11. The trade steps show the coin, the network and the token contract to both traders, and the
    volume in units of the coin. They take the coin from the payment method of the offer, not from
    data of the peer. Payment account data of the seller which is not of the payment method of the
    offer is not shown to the buyer, and the buyer cannot start the payment with it.
12. The API does not create accounts of a stablecoin payment method. It leaves these payment
    methods out of its payment method list and refuses their account form.
13. The application states the issuer risk when a payment account is created and before an offer
    is created or taken with it: the issuer of a coin may be able to freeze the coins of an
    address, also after a trade, and neither Bisq nor a mediator can undo that.

## Listed rails

Listed are ten stablecoins which are pegged to USD, each with a supply of more than 1.4 billion
USD (DefiLlama, 2026-11-17). The supply is a rough guide for the list, not a rule. A coin is listed
on the networks with the largest stablecoin supply (Ethereum, Tron, Solana, BNB Smart Chain) where
it has at least 10% of its supply or 1 billion USD.

| Rail | Coin | Network | Token contract |
|---|---|---|---|
| `USDT_ERC20` | Tether USD | Ethereum | `0xdAC17F958D2ee523a2206206994597C13D831ec7` |
| `USDT_TRC20` | Tether USD | Tron | `TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t` |
| `USDT_BEP20` | Tether USD, bridged by Binance | BNB Smart Chain | `0x55d398326f99059fF775485246999027B3197955` |
| `USDT_SPL` | Tether USD | Solana | `Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB` |
| `USDC_ERC20` | USD Coin | Ethereum | `0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48` |
| `USDC_SPL` | USD Coin | Solana | `EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v` |
| `USDC_BEP20` | USD Coin, bridged by Binance | BNB Smart Chain | `0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d` |
| `USDS_ERC20` | Sky Dollar | Ethereum | `0xdC035D45d973E3EC169d2276DDab16f1e407384F` |
| `USDE_ERC20` | Ethena USDe | Ethereum | `0x4c9EDD5852cd905f086C759E8383e09bff1E68B3` |
| `DAI_ERC20` | Dai | Ethereum | `0x6B175474E89094C44Da98b954EedeAC495271d0F` |
| `RLUSD_ERC20` | Ripple USD | Ethereum | `0x8292Bb45bf1Ee4d140127049757C2E0fF06317eD` |
| `USDG_ERC20` | Global Dollar | Ethereum | `0xe343167631d89B6Ffc58B88d6b7fB0228795491D` |
| `USDG_SPL` | Global Dollar | Solana | `2u1tszSeqZ3qBWF3uNGPFc8TzMk2tdiwknnRMWGWjGWH` |
| `PYUSD_ERC20` | PayPal USD | Ethereum | `0x6c3ea9036406852006290770BEdFcAbA0e23A0e8` |
| `PYUSD_SPL` | PayPal USD | Solana | `2b1kV6DkPAnxd5ixfnxCpjxmKwqjjaYmCZfHsFu24GXo` |
| `U_ERC20` | United Stables | Ethereum | `0xcE24439F2D9C6a2289F741120FE202248B666666` |
| `U_BEP20` | United Stables | BNB Smart Chain | `0xcE24439F2D9C6a2289F741120FE202248B666666` |
| `USDF_ERC20` | Falcon USD | Ethereum | `0xFa2B947eEc368f42195f24F36d2aF29f7c24CeC2` |

A new rail is added at the end of the payment method list of the trade statistics. The trade
statistics store a payment method as its position in that list, so an entry never moves.

## Rationale

- **Payment method of the peg currency.** As an altcoin a stablecoin has a price in BTC per coin
  with 8 decimals. One USD is about 1,300 satoshi at 77,000 USD per BTC, so one price step is
  0.077% and grows with the BTC price, and a price in USD cannot be stored exactly. A network is
  part of the ticker there, so the same coin on another network is another market. As a payment
  method of USD the price is that of every USD offer, a network is a payment method of one market,
  and a client without this change reads such an offer as a USD offer with an unknown payment
  method.
- **One unit of the coin for one unit of the peg currency.** It keeps the price, the volume and the
  trade protocol of a fiat offer unchanged. A trader who expects a coin to leave its peg sets the
  price accordingly or does not use the payment method.
- **Trade limit and issuer risk.** The limit classes follow the chargeback risk of a payment
  method, and a transfer on a blockchain has none. The risk that an issuer freezes coins stays
  with the trader who holds them. It is not lowered by a smaller trade limit, so it is stated in
  the warnings (rule 13) instead.
- **IDs.** The names follow the scheme of the stablecoin rails of Bisq 2 (code and token standard),
  so both applications name a rail the same way. Six of the rails exist in Bisq 2 under the same
  name; the others are not listed there.
- **Fiat rules where no rule is needed.** A stablecoin payment method differs from a bank transfer
  in the chargeback risk. The lock time could be the shorter one of altcoins, as the payment is as
  fast. It stays that of the fiat payment methods to keep this change free of protocol rules of
  its own. Both traders derive it from the payment method of the offer, so it can only be changed
  in a release which all traders of these payment methods run.
- **Address form.** An Ethereum address is the same address in any case of its letters, but
  account data, its hash in the contract and its account age witness are made from the text.
  Stored in one form, they do not depend on how the address was entered. The form with the
  checksum also gives the peer an address in which a wallet finds a mistyped digit.
- **Token contract.** Anyone can create a token with the name of a listed coin. The token contract
  is the only way to tell them apart, so it is part of the rail and shown to both traders.

## Compatibility

- A client without this change shows an offer with a stablecoin payment method as an offer of the
  peg currency with an unknown payment method and cannot take it. It accepts the trade statistics
  of such trades and counts them as trades of the peg currency.
- The account data is a new message in the payment account payload (field 42), which is also part
  of the canonical encoding for the contract hash. A client without this change cannot read an
  account, a trade, a contract or a dispute which holds such account data. Mediators and refund
  agents therefore need a client with this change before the first trade with such a payment
  method.
- A client without this change shows the payment method of such a trade statistic as a number.
- The accounts, offers and trade statistics of the altcoin markets of stablecoins are not changed.

## Known gaps

- A coin can trade above or below its peg currency. A percentage offer follows the BTC price in
  the peg currency, so a maker who does not watch the market sells or buys at a wrong price while a
  coin is off its peg. No offer is deactivated for that reason.
- The token contracts are taken from two public token lists (CoinGecko and the Trust Wallet token
  list, for `RLUSD_ERC20` DefiLlama), not from the documentation of the issuers. They must be
  checked against that documentation before a release.
- The address of the peer is not validated when it arrives. It is shown as text. A client which
  does not follow rule 5 can send an Ethereum or BNB Smart Chain address with all letters in one
  case, which has no checksum and is other account data than the same address in the stored form.
  A Solana address has no checksum.
- An address on Ethereum is also an address on BNB Smart Chain. Only the payment method of the
  offer fixes the network.
- The trade steps do not ask the buyer for the transaction ID of the transfer. The message for
  the started payment has a field for it, which is filled only for XMR. Two trades with the same
  address and volume cannot be told apart by the seller from the transfer alone.
- A ban of payment account data in the filter is per payment method. An address on Ethereum is
  valid for every rail on Ethereum and BNB Smart Chain, so it needs one ban entry per rail, and a
  new address costs nothing.
- Outside the trade steps (offer book, offer and trade details, portfolio lists) a volume names the
  peg currency, not the coin.
- The issuer of a coin may be able to freeze the coins of an address. Mediation cannot undo that.
  Which of the listed coins can be frozen is not recorded.
- A taker accepts an offer above the tolerated small amount only if the account age witness of the
  maker is known to the taker, as for every fiat payment method. Offers of a new account can show
  as not takeable until its witness has spread.
- The same coin can be traded in two ways, as an altcoin market and as a payment method of its peg
  currency, so its offers are split between two offer books.
- Coins on other networks are not listed, among them USDC on Base and Arbitrum, which hold more
  than several listed rails. Their token standard is also ERC-20, so their rails need a naming
  rule first.
- The API cannot create these accounts (rule 12).
