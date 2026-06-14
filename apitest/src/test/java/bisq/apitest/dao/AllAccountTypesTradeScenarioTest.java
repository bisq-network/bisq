/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest.dao;

import bisq.cli.GrpcClient;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.util.List;
import java.util.function.UnaryOperator;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Long-running stability test: for every CLI-creatable payment-account type, create a
 * matching account on both peers and run the full v1 trade flow across all four role
 * combinations, so each peer is exercised as maker AND taker, BTC buyer AND BTC seller:
 *
 * <ol>
 *   <li>Alice maker SELL → Bob taker  (Alice maker+BTC-seller, Bob taker+BTC-buyer)</li>
 *   <li>Alice maker BUY  → Bob taker  (Alice maker+BTC-buyer,  Bob taker+BTC-seller)</li>
 *   <li>Bob maker SELL   → Alice taker (Bob maker+BTC-seller,  Alice taker+BTC-buyer)</li>
 *   <li>Bob maker BUY    → Alice taker (Bob maker+BTC-buyer,   Alice taker+BTC-seller)</li>
 * </ol>
 *
 * <p>Account types use a BTC maker/taker fee (not BSQ) so the suite does not deplete the
 * peers' BSQ across the many trades it drives.
 *
 * <p>Only account types whose form fields match the schema {@code createPaymentAccount}
 * accepts are exercised — SEPA / FasterPayments are intentionally excluded for the same
 * reason {@link bisq.apitest.method.payment.CreatePaymentAccountTest} excludes them
 * (the server rejects their JSON form on this build). The trade flow is identical across
 * types, so this set covers the regression surface (geo, mobile, digital-wallet, Asia
 * bank, LATAM bank) without exhaustively enumerating every fiat method.
 *
 * <p>Runs on a freshly reset stack (@Tag("freshstack")) for the same maker-sync reason
 * documented on {@link TradeScenarioTest}.
 */
@Slf4j
@Tag("freshstack")
@Tag("longrunning")
public class AllAccountTypesTradeScenarioTest extends DaoTestBase {

    private static final double SECURITY_DEPOSIT_PCT = 15.0;

    // Default amount for methods with no chargeback risk (F2F, AdvancedCash, JapanBank):
    // a brand-new account trades up to its full risk-based limit (>= 0.125 BTC), so 0.0125
    // is comfortably under.
    private static final long DEFAULT_AMOUNT_SATS = 1_250_000L; // 0.0125 BTC
    // Amount for chargeback-risk methods (Revolut, NationalBank): a brand-new, unsigned
    // account BUYING BTC is capped at TOLERATED_SMALL_TRADE_AMOUNT (0.002 BTC) by
    // AccountAgeWitnessService.getTradeLimit. Stay just under the cap (and well above
    // trade fees, so the buyer's net BTC still rises and the flow's balance assertion holds).
    private static final long CHARGEBACK_AMOUNT_SATS = 195_000L; // 0.00195 BTC

    /** A payment-account type plus the currency/price/amount used to trade it. */
    private static final class AccountType {
        final String label;
        final String methodId;
        final String currency;
        final String fixedPrice;  // currency units per BTC
        final long amountSats;    // trade amount; constrained by the method's new-account limit
        /** Builds the createPaymentAccount JSON for a given unique account name. */
        final UnaryOperator<String> jsonForName;

        AccountType(String label, String methodId, String currency, String fixedPrice,
                    long amountSats, UnaryOperator<String> jsonForName) {
            this.label = label;
            this.methodId = methodId;
            this.currency = currency;
            this.fixedPrice = fixedPrice;
            this.amountSats = amountSats;
            this.jsonForName = jsonForName;
        }
    }

    private static final List<AccountType> ACCOUNT_TYPES = List.of(
            new AccountType("F2F", "F2F", "USD", "50000", DEFAULT_AMOUNT_SATS, name -> "{"
                    + "\"paymentMethodId\":\"F2F\","
                    + "\"accountName\":\"" + name + "\","
                    + "\"city\":\"Anytown\","
                    + "\"contact\":\"Morse Code\","
                    + "\"country\":\"US\","
                    + "\"extraInfo\":\"all-types-test\"}"),
            new AccountType("Revolut", "REVOLUT", "EUR", "45000", CHARGEBACK_AMOUNT_SATS, name -> "{"
                    + "\"paymentMethodId\":\"REVOLUT\","
                    + "\"accountName\":\"" + name + "\","
                    + "\"userName\":\"" + name + "\","
                    + "\"tradeCurrencies\":\"USD,EUR,GBP\","
                    + "\"selectedTradeCurrency\":\"EUR\"}"),
            new AccountType("AdvancedCash", "ADVANCED_CASH", "RUB", "4000000", DEFAULT_AMOUNT_SATS, name -> "{"
                    + "\"paymentMethodId\":\"ADVANCED_CASH\","
                    + "\"accountName\":\"" + name + "\","
                    + "\"accountNr\":\"0000 1111 2222\","
                    + "\"tradeCurrencies\":\"USD,EUR,RUB\","
                    + "\"selectedTradeCurrency\":\"RUB\"}"),
            new AccountType("JapanBank", "JAPAN_BANK", "JPY", "7000000", DEFAULT_AMOUNT_SATS, name -> "{"
                    + "\"paymentMethodId\":\"JAPAN_BANK\","
                    + "\"accountName\":\"" + name + "\","
                    + "\"bankName\":\"Test Bank\","
                    + "\"bankCode\":\"1234\","
                    + "\"bankBranchName\":\"Branch\","
                    + "\"bankBranchCode\":\"567\","
                    + "\"bankAccountType\":\"Futsu\","
                    + "\"bankAccountName\":\"Holder\","
                    + "\"bankAccountNumber\":\"7654321\"}"),
            new AccountType("BrazilNationalBank", "NATIONAL_BANK", "BRL", "250000", CHARGEBACK_AMOUNT_SATS, name -> "{"
                    + "\"paymentMethodId\":\"NATIONAL_BANK\","
                    + "\"accountName\":\"" + name + "\","
                    + "\"country\":\"BR\","
                    + "\"bankName\":\"Banco do Brasil\","
                    + "\"branchId\":\"456789-10\","
                    + "\"holderName\":\"Pedro\","
                    + "\"accountNr\":\"456789-87\","
                    + "\"nationalAccountId\":\"222222222\","
                    + "\"holderTaxId\":\"111.222.333-44\"}"));

    @Test
    public void tradeAllAccountTypesAcrossAllRoles() {
        for (AccountType type : ACCOUNT_TYPES) {
            PaymentAccount aliceAcct = createAccount(alice, type, "alice");
            PaymentAccount bobAcct = createAccount(bob, type, "bob");
            log.info("=== trading account type {} ({}) ===", type.label, type.currency);

            // 1. Alice maker SELL, Bob taker.
            runOnce(type, alice, aliceAcct, bob, bobAcct, "SELL");
            // 2. Alice maker BUY, Bob taker.
            runOnce(type, alice, aliceAcct, bob, bobAcct, "BUY");
            // 3. Bob maker SELL, Alice taker.
            runOnce(type, bob, bobAcct, alice, aliceAcct, "SELL");
            // 4. Bob maker BUY, Alice taker.
            runOnce(type, bob, bobAcct, alice, aliceAcct, "BUY");
        }
    }

    private void runOnce(AccountType type,
                         GrpcClient maker, PaymentAccount makerAcct,
                         GrpcClient taker, PaymentAccount takerAcct,
                         String direction) {
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> maker.createFixedPricedOffer(
                direction, type.currency, type.amountSats, type.amountSats, type.fixedPrice,
                SECURITY_DEPOSIT_PCT, makerAcct.getId(), "BTC"));
        V1TradeFlow.runV1Trade(dao, maker, taker, offer, type.currency,
                type.amountSats, takerAcct.getId());
    }

    /** Create (or reuse) a payment account of {@code type} on {@code client}. */
    private PaymentAccount createAccount(GrpcClient client, AccountType type, String owner) {
        // Reuse an existing same-method account if one is already present (idempotent reruns).
        for (PaymentAccount existing : client.getPaymentAccounts()) {
            if (type.methodId.equals(existing.getPaymentMethod().getId())) return existing;
        }
        String accountName = owner + "-" + type.methodId + "-" + System.nanoTime();
        return client.createPaymentAccount(type.jsonForName.apply(accountName));
    }
}
