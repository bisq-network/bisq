# Custom payment method

## Scope

This specification defines the payment method with which two traders use a payment method that
Bisq does not support: the two texts of its account, which accounts and offers belong together, the
checks on the data of the trading peer, the trade limit and trade period, and what the user is told
about the risks. It does not define how a payment with such a payment method is made or proven.
Bisq knows nothing about that.

## Terms

- The **custom payment method** is the payment method with the ID `CUSTOM`.
- The **name** is the text with which a trader names the payment method that is meant, for example
  `M-Pesa`.
- The **account data** is the text which tells the BTC buyer how to pay the BTC seller, and the BTC
  seller from which account the payment comes.
- A **payment method of Bisq** is any other payment method of Bisq, including the removed ones.

## Rules

### Payment method and account

1. There is one custom payment method for all names. Its ID `CUSTOM` is never renamed, reused or
   removed, as it is stored in accounts, offers, contracts and trade statistics.
2. It is a fiat payment method and supports every fiat currency of Bisq. An account has one
   currency.
3. An account holds the name and the account data. Both are free texts of the account owner. Bisq
   gives them no meaning and does not check what they say.
4. All other rules are those of a fiat payment method:
   - the account has an account age witness. Its input is the payment method ID and the account
     data, without the name;
   - the seller validates the account of the buyer before the deposit is published (see
     [`fiat-buyer-payment-account-validation.md`](../trade/fiat-buyer-payment-account-validation.md));
   - the lock time of the delayed payout transaction is that of the fiat payment methods;
   - a trade statistic has `CUSTOM` as payment method. It does not contain the name.

### The two texts

5. A name is valid if it
   - has at most 20 characters, counted as UTF-16 code units,
   - contains at least one letter or number which is compared by rule 7,
   - has no more than 4 marks in a row which are drawn above or below a character (non-spacing
     and enclosing marks). They are counted in the decomposed form (NFKD), in which a letter with
     an accent is the letter and a mark. A zero width joiner or non-joiner does not end the row,
     and
   - contains none of these characters:
     - line break, tab or another control character,
     - format character, except the zero width joiner and the zero width non-joiner, which some
       scripts need inside a word,
     - private use character or unpaired surrogate,
     - character which the Java runtime does not know (unassigned),
     - letter or mark which is not shown: the Hangul fillers, the variation selectors, the
       combining grapheme joiner and the Khmer inherent vowels.
6. Account data is valid if it has at most 1000 characters, counted as UTF-16 code units, contains
   at least one letter or number after NFKC, and contains only the characters allowed in a name,
   line feed, carriage return and tab.
7. Two names are the **same name** if both are valid and these steps give the same text for
   both: keep the letters, numbers and marks; compatibility normalization (NFKC); lower case of
   each character, upper case of the text, lower case of each character; NFKC; keep the letters,
   numbers and marks; replace each decimal digit by the ASCII digit of its value.
   - Upper and lower case, spaces, punctuation and symbols make no difference: `M-Pesa` and
     `m pesa` are the same name, and so are `Joe's Pay` and `Joe´s Pay`. A symbol which NFKC
     would turn into letters, such as the trade mark sign, is dropped as well.
   - Numbers of every kind are compared, and decimal digits by their value: `Bank 1` and
     `Bank ۱` (Persian digit one) are the same name.
   - One pair of case forms stays apart: the capital I with dot above (U+0130) compares as `i`,
     so it is not the same as `i` followed by a combining dot above (U+0069 U+0307).
8. When an account is created, the name must not be the same name as the ID, the name or the short
   name of a payment method of Bisq, in the language of the user or in English. This includes the
   removed payment methods OKPay, Cash App, Venmo and Chase QuickPay, and the custom payment method
   itself.
9. Rule 8 applies only when an account is created. An account stays valid when a later version of
   Bisq adds a payment method with its name. Rules 5 and 6 apply to every text, also to the texts
   of other traders.
10. Before the account stores a text which the user entered, the characters which are not allowed
    and the white space around the text are removed. A text which is copied from a web page or a
    document can contain such characters, and most of them are not visible.
11. An imported account is not checked when it is imported, and the warning of rule 23 is not
    shown for it. Rule 8 is not applied to it. By rules 12 and 14 it cannot be used for an offer
    or to take one unless its name and its account data are valid.

### Offers

12. An offer of an account of the custom payment method carries the name of that account in its
    extra data, with the key `reserved0`. No offer is created for an account without a valid name
    or without valid account data.
13. An offer of the custom payment method without a valid name has no name. It matches no account
    and cannot be taken.
14. An account matches an offer of the custom payment method only if it is an account of the custom
    payment method, has the currency of the offer, has the same name as the offer (rule 7) and has
    valid account data.
15. The name of an offer is always shown together with the name of the custom payment method, for
    example `Custom: M-Pesa`. A name alone is never shown as the payment method of an offer.

### Trades

16. The name of a trade is the name of its offer. Both traders have agreed to the offer, and it is
    part of the contract. The payment step of the BTC buyer and the payment method of the trade
    details show that name. Where the account of a trader is shown as a whole, it is shown with
    the name in that account, which is the same name by rule 17.
17. In a custom trade, the seller must include its account with the deposit and delayed payout
    transactions. A missing account is rejected before any change of the buyer's wallet or trade.
    When the peer reveals its payment account, the node checks before it uses or stores it that
    - the account is an account of the custom payment method if, and only if, the offer is an offer
      of the custom payment method. This holds for the kind of the account and for the payment
      method ID in it, which the peer sets independently of each other,
    - the name of the account is the same name as the name of the offer, and
    - the account data is valid.
18. If a check of rule 17 fails, the trade step fails. The buyer fails it before any change of its
    wallet or of the trade, and does not share its own account. The seller does not treat the
    account of the buyer as validated, so it neither publishes the deposit nor accepts the payment
    as started, nor pays out. In both cases the deposit is not published: the seller publishes it
    only after it has validated the account of the buyer (see
    [`fiat-buyer-payment-account-validation.md`](../trade/fiat-buyer-payment-account-validation.md)).
    Both trade fees are paid by then and are lost, and the offer of the maker is removed.
19. An error of rule 18 does not contain a text of the peer.

### Limits

20. The trade limit is the limit of the highest risk category, but at most 0.05 BTC. The 0.05 BTC
    are not adjusted to the trade limit of the DAO.
21. The custom payment method is not in the list of payment methods with chargeback risk. Its trade
    limit does not depend on the age or the signing of the account.
22. The trade period is 4 days.

### What the user is told

23. A new account is saved only after the user has confirmed a warning which says that
    - custom payment methods are defined by the traders and carry higher risks than the payment
      methods of Bisq,
    - mediators and arbitrators might not be able to help, because they might not be familiar with
      the payment method or because there is no good way to prove a payment,
    - the BTC buyer might reverse the payment after the trade (chargeback), and the BTC seller then
      loses the trade amount,
    - Bisq checks neither the name nor the account data, and
    - what the trade limit is.
24. A trader who creates or takes an offer with an account of the custom payment method is warned
    again, the BTC buyer about the proof of payment and the BTC seller about the chargeback risk.
    The user can choose not to see this warning again.
25. The BTC buyer is reminded to keep a proof of payment when the payment is due. The BTC seller is
    warned about the chargeback risk before the receipt of the payment is confirmed.
26. The offer book tells for an offer of the custom payment method that the name is a text of the
    maker and that Bisq does not support the payment method.
27. The API does not create accounts of the custom payment method. It leaves the payment method out
    of its payment method list and refuses its account form. An account which was created in the
    desktop application can be used with the API. The API takes an offer only with an account
    which matches it (rule 14), as the desktop does.

## Rationale

- **One payment method, the name as data.** A payment method ID is a key in closed sets: the
  display strings, the payment method list of the trade statistics, the bans of the filter, the
  list of payment methods with chargeback risk and the comparison of both payment methods in the
  contract. A free text as ID would have to be handled in each of them.
- **The reserved key.** A client since v1.10.4 refuses an offer whose extra data has a key it does
  not know. `reserved0` is one of the keys which were reserved for later use, so such a client
  accepts and relays the offer.
- **The name of the offer counts.** The offer is what the taker saw and what the contract
  contains. A name in the account of the peer arrives after the trade has started.
- **Comparison of names.** Two traders type the same name independently, so spelling details must
  not keep them apart. Marks are compared, as they are parts of words in scripts such as
  Devanagari or Thai. Java has no case folding, so the case is removed in three steps: the lower
  case of each character first, as the capital sharp s is its own upper case; then the upper
  case of the text, which writes a sharp s as two letters; then the lower case of each character
  on its own, as the lower case of a whole text depends on the place of a letter in its word (the
  final sigma of Greek). The second normalization puts letters and accents together again which
  the case conversion took apart. Symbols are dropped before the first normalization, as it would
  turn some of them into letters or marks: the trade mark sign into TM, the spacing acute accent,
  which some keyboards give for an apostrophe, into a space and an accent. Otherwise `M-Pesa™`
  would not be `M-Pesa`, while `Pay™` would be `Paytm`. A decimal digit counts by its value, as a
  trader types the digits of the own script or those of the Latin script.
- **Names of payment methods of Bisq.** The custom payment method is for payment methods which
  Bisq does not cover. A payment method of Bisq has its own account data, trade limit, trade period
  and, for some, the signing rule. Venmo and Cash App were removed for their chargeback risk.
- **The API checks the account of the taker.** The API gets the account as an ID. Before this
  change it did not check that the account matches the offer. For other payment methods the
  contract refuses an account of another payment method before the taker pays its fee. An account
  of another custom payment method has the payment method ID of the offer, so it would be refused
  only by rule 17, after both traders have paid their fee.
- **Rule 8 only at creation.** A rule which changes with every new payment method cannot be a rule
  for the data of other traders: nodes of different versions would disagree about the same offer,
  and an existing account would stop working.
- **Characters.** Control and format characters can break a line, hide text or change the order in
  which a text is shown. The name is shown in the offer book of every user. A letter or mark which
  is not shown would be compared, so a name with it would differ from a name which looks the same,
  and a name of such letters alone would be shown as an empty name. Many marks above or below
  one character are drawn over the lines next to it. Words of Tibetan, Burmese and pointed Hebrew
  have up to 3 such marks on one character, and 4 with a cantillation mark of Hebrew. Fully
  pointed Hebrew with a meteg and Sanskrit in Tibetan script can have 5 or more, but names of
  payment methods are not written that way, so the limit is 4. Marks with a width of their own
  are not counted, as words of Burmese have 5 marks in a row of which 2 are above or below. The
  account data has no such limit: it is not shown in the offer book, only in the trade, in a
  dispute and in the account of its owner. A character which a runtime does not know can be a
  letter for a newer runtime, and the two would compare a name with it differently.
- **Trade limit.** Bisq cannot assess the risk of a payment method it does not know, so the limit
  is the lowest one of the risk categories and is capped. Account signing is not used: an account
  is signed by a seller whose account of the same payment method is signed already, so signing
  would not start for a payment method which few traders use, and buyers would stay at the limit of
  unsigned accounts. The price of that choice: in EUR, USD, GBP, CAD, AUD and BRL an unsigned
  buyer with a payment method of the chargeback list is limited to 0.002 BTC, and a buyer with a
  new custom account to 0.05 BTC, 25 times as much. A limit of 0.002 BTC for buyers of the custom
  payment method in these currencies was considered and dropped: without signing it would hold
  every custom trade in these currencies at 0.002 BTC, and a buyer can only trade with a seller who
  has set up an account with the same name and confirmed the warning of rule 23.
- **Trade period.** The speed of an unknown payment method is not known. 4 days is the period of
  the custom payment rail of Bisq 2.
- **Account age witness input.** It is the input which Bisq 2 uses for its user defined account,
  so that the age of such an account can be verified there.
- **Lengths.** They are those of Bisq 2: 20 characters for the name of a custom payment method and
  1000 characters for the data of a user defined account.

## Compatibility

- A client since v1.10.4 without this change accepts and relays an offer of the custom payment
  method. It shows it as an offer with an unknown payment method and cannot take it.
- A client before v1.10.4 has no list of extra data keys and keeps the extra data as it received
  it (read in the source of v1.10.3), so it accepts and relays such an offer. This was not run.
- The account data is a new message in the payment account payload (field 43), which is also part
  of the canonical encoding for the contract hash. Field 42 is left free. A client without this
  change does not load an account of this kind.
- A mediator or refund agent needs a client with this change. A client without it cannot read a
  dispute of a trade with the custom payment method.
- A client without this change accepts the trade statistics of such trades. It shows the position
  of the payment method in the payment method list instead of its name.

## Known gaps

- Rule 8 compares names only. A user gets around it with a changed name, for example
  `SEPA transfer`, with a name in a third language, or with letters of another script which look
  the same. Nothing can check what a free text means.
- The custom payment method lets traders use payment methods which Bisq left out or removed for
  their chargeback risk, and it lets a buyer use a payment method of the chargeback list under
  another name without the signing rule (see the rationale of the trade limit). Only the warnings
  and the trade limit stand against that.
- The filter can ban the custom payment method as a whole. It can ban a revealed account by its
  exact name or its exact account data, which one changed character in the free text evades. It
  cannot ban an offer by its name.
- The name is a free text in the offer book. It can carry up to 20 characters of advertising. The
  extra info of face to face and cash by mail offers can carry more.
- The protocol carries no proof of payment, and nothing tells a mediator which rules a custom
  payment method has.
- The account data of the peer is checked for its length, its characters and for one letter or
  number only.
- A contract which is received with a dispute holds the accounts of both traders. A dispute agent
  does not apply rule 17 to them, and a trader checks them against the hashes in the contract
  only. The views show the texts of such an account as plain text.
- The texts of an account are plain text, but a popup turns a link tag in its message into a
  link. This change puts no text of the peer into a popup message. The error for an account which
  the filter has banned does contain the payment details of the peer, as for every payment method.
- An offer with a name which is not valid stays in the offer book. It is shown without a name, and
  a click on it tells that it cannot be taken.
- The API user sees none of the warnings. An account which was created in the desktop application
  can be used for offers and trades with the API.
- Rule 17 is checked when an account is revealed. At that time both traders have paid their trade
  fee. A taker with a modified client can take an offer with an account of another name and
  make the trade fail this way. The taker loses its own fee as well.
- A seller with a modified client can reveal an account which fails rule 17 and publish the
  deposit all the same. The buyer then has a trade with a deposit but without the account of the
  seller, as when the message of the seller does not arrive, and needs mediation. The seller has
  locked its own funds as well.
- For other payment methods the payment method of the revealed account is still not compared with
  the payment method of the contract. Rule 17 only keeps accounts of the custom payment method out
  of trades of other payment methods, and the reverse.
- The 0.05 BTC of rule 20 are a fixed BTC amount. They do not follow the BTC price.
- The normalization, the case conversion and the classes of characters follow the Unicode tables
  of the Java runtime (Java 21: Unicode 15.0). A character which a runtime does not know is
  refused. An offer with such a character in its name has no name for that client, and account
  data with it is refused by rule 17, after both trade fees are paid. Two versions of Bisq with
  different runtimes can also disagree about a character whose properties Unicode has changed in
  between. A test pins the tables of Java 21, so a change of the runtime shows up as a failing
  test. It then needs a plan for peers on the old runtime.
- An imported account is not checked and its warning is not shown (rule 11). If its account data
  is not valid, it matches no offer, and the offer book tells the user that no account with the
  name of the offer exists. An offer with it fails with a general error.
- Letters of different scripts which look the same are different letters, and a name with an
  accent is another name than the name without it.
- Some letters have variants which different keyboards give, for example the Arabic kaf (U+0643)
  and the Persian keheh (U+06A9), or the Arabic yeh (U+064A) and the Persian yeh (U+06CC). They are
  different letters, so a Persian name typed on an Arabic keyboard is another name.
- A spacing accent is a symbol and is dropped. The spacing voiced sound mark of Japanese (U+309B)
  after a katakana gives the name of the katakana alone, not of the voiced letter. Input methods
  write the voiced letter.
