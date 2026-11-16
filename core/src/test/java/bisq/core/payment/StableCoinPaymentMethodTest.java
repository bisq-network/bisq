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
package bisq.core.payment;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.StableCoinAccountPayload;
import bisq.core.payment.stablecoin.StableCoin;
import bisq.core.payment.stablecoin.StableCoinPaymentRail;
import bisq.core.proto.CoreProtoResolver;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StableCoinPaymentMethodTest {
    private static final String TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    @BeforeAll
    public static void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void everyRailHasOnePaymentMethodWithItsName() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            assertTrue(PaymentMethod.getActivePaymentMethod(rail.name()).isPresent(), rail.name());
            assertEquals(1, PaymentMethod.getPaymentMethods().stream()
                    .filter(paymentMethod -> paymentMethod.getId().equals(rail.name()))
                    .count(), rail.name());
        }
        assertEquals(StableCoinPaymentRail.values().length, PaymentMethod.getPaymentMethods().stream()
                .filter(PaymentMethod::isStableCoin)
                .count());
    }

    // A stablecoin payment method belongs to the fiat market of its peg currency. It must not be handled as an
    // altcoin payment method, which would list it in the altcoin tabs and change the lock time of the trade.
    @Test
    public void stableCoinPaymentMethodIsAFiatPaymentMethodWithoutChargebackRisk() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(rail.name());
            assertTrue(paymentMethod.isStableCoin(), rail.name());
            assertTrue(paymentMethod.isFiat(), rail.name());
            assertFalse(paymentMethod.isBlockchain(), rail.name());
            assertFalse(paymentMethod.isAltcoin(), rail.name());
            assertFalse(PaymentMethod.hasChargebackRisk(paymentMethod, "USD"), rail.name());
            assertFalse(PaymentMethod.hasChargebackRisk(paymentMethod), rail.name());
        }
    }

    @Test
    public void otherPaymentMethodsAreNoStableCoin() {
        assertFalse(paymentMethod(PaymentMethod.SEPA_ID).isStableCoin());
        assertFalse(paymentMethod(PaymentMethod.CELPAY_ID).isStableCoin());
        assertFalse(paymentMethod(PaymentMethod.BLOCK_CHAINS_ID).isStableCoin());
        assertFalse(paymentMethod(PaymentMethod.BLOCK_CHAINS_INSTANT_ID).isStableCoin());
        assertFalse(paymentMethod(PaymentMethod.BSQ_SWAP_ID).isStableCoin());
        assertFalse(PaymentMethod.getDummyPaymentMethod("NOT_LISTED_ERC20").isStableCoin());
        assertFalse(PaymentMethod.getDummyPaymentMethod("usdt_trc20").isStableCoin());
    }

    // The limit class is compared by the default limit of the payment method, from which the risk factor is derived.
    // The limit in BTC depends on the trade limits of the DAO, which are a static instance shared by all tests.
    @Test
    public void tradeLimitAndTradePeriodAreThoseOfTheAltcoinPaymentMethod() {
        PaymentMethod altcoins = paymentMethod(PaymentMethod.BLOCK_CHAINS_ID);
        long defaultLimitOfAltcoins = altcoins.toProtoMessage().getMaxTradeLimit();
        assertTrue(defaultLimitOfAltcoins >
                paymentMethod(PaymentMethod.SEPA_ID).toProtoMessage().getMaxTradeLimit());

        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(rail.name());
            assertEquals(TimeUnit.HOURS.toMillis(24), paymentMethod.getMaxTradePeriod(), rail.name());
            assertEquals(altcoins.getMaxTradePeriod(), paymentMethod.getMaxTradePeriod(), rail.name());
            assertEquals(defaultLimitOfAltcoins, paymentMethod.toProtoMessage().getMaxTradeLimit(), rail.name());
        }
    }

    @Test
    public void tradeCurrencyIsThePegCurrency() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(rail.name());
            FiatCurrency pegCurrency = new FiatCurrency(rail.getStableCoin().getPegCurrencyCode());
            assertEquals(List.of(pegCurrency), PaymentAccountUtil.getTradeCurrencies(paymentMethod), rail.name());
            assertTrue(PaymentAccountUtil.supportsCurrency(paymentMethod, pegCurrency), rail.name());
            assertFalse(PaymentAccountUtil.supportsCurrency(paymentMethod, new FiatCurrency("CHF")), rail.name());
        }
    }

    // Many views show a payment method with Res.get(id) and Res.get(id + "_SHORT"). The texts of all rails have one
    // form, e.g. "Tether USD (USDT, Ethereum ERC-20)" and "USDT (ERC-20)".
    @Test
    public void displayStringsMatchTheRegistry() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            StableCoin coin = rail.getStableCoin();
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(rail.name());
            String tokenStandard = coin.getTokenStandard().getDisplayName();
            assertEquals(coin.getName() + " (" + coin.getCode() + ", " + coin.getNetwork().getDisplayName() + " " +
                    tokenStandard + ")", paymentMethod.getDisplayString(), rail.name());
            assertEquals(coin.getCode() + " (" + tokenStandard + ")", paymentMethod.getShortName(), rail.name());
        }
    }

    // The issuer of a coin may be able to freeze coins, which mediation cannot undo. The trade limit does not
    // account for that, so the texts shown for a new account and before an offer is created or taken must say it.
    @Test
    public void warningTextsNameTheIssuerRisk() {
        StableCoinAccount account = newAccount(StableCoinPaymentRail.USDT_TRC20, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
        for (String key : List.of("payment.stablecoin.info.account",
                account.getMessageForBuyer(),
                account.getMessageForSeller())) {
            String text = Res.get(key);
            assertTrue(text.contains("Issuer risk"), key);
            assertTrue(text.contains("freeze"), key);
            assertTrue(text.contains("Bisq and the mediators have no influence on that"), key);
        }
    }

    // The texts of the trade steps take arguments, so a quote or a brace in them would break the formatting
    @Test
    public void tradeStepTextsTakeTheirArguments() {
        String buyerText = Res.get("portfolio.pending.step2_buyer.stablecoin",
                "1000 USDT", "Tron (TRC-20)", "Tether USD (USDT)");
        assertTrue(buyerText.startsWith("Please transfer 1000 USDT from your external wallet"), buyerText);
        assertTrue(buyerText.contains("Use the network Tron (TRC-20) and send only Tether USD (USDT) with"), buyerText);

        String sellerText = Res.get("portfolio.pending.step3_seller.stablecoin",
                "PART1 ", "Tron (TRC-20)", "MY_ADDRESS", "1000 USDT", "TOKEN_CONTRACT");
        assertTrue(sellerText.startsWith("PART1 Please check in your wallet or in a block explorer of the network " +
                "Tron (TRC-20) if the transfer to your address\nMY_ADDRESS\nhas arrived and is final."), sellerText);
        assertTrue(sellerText.contains("The payment amount has to be 1000 USDT of the token with the token " +
                "contract\nTOKEN_CONTRACT\n\n"), sellerText);
    }

    @Test
    public void factoryCreatesAStableCoinAccountOfTheRail() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(rail.name());
            PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
            assertTrue(paymentAccount instanceof StableCoinAccount, rail.name());
            assertFalse(paymentAccount instanceof AssetAccount, rail.name());
            paymentAccount.init();

            StableCoinAccount account = (StableCoinAccount) paymentAccount;
            assertEquals(rail.getStableCoin(), account.getStableCoin(), rail.name());
            assertEquals(paymentMethod, account.getPaymentMethod(), rail.name());
            assertEquals(new FiatCurrency("USD"), account.getSingleTradeCurrency(), rail.name());
            assertEquals(new FiatCurrency("USD"), account.getSelectedTradeCurrency(), rail.name());
            assertTrue(account.getPaymentAccountPayload() instanceof StableCoinAccountPayload, rail.name());
            assertEquals(rail.name(), account.getPaymentAccountPayload().getPaymentMethodId(), rail.name());
            assertFalse(account.getPaymentAccountPayload().showRefTextWarning(), rail.name());
            // Res.get returns the key if there is no text for it
            assertTrue(Res.getResourceBundle().containsKey(account.getMessageForBuyer()));
            assertTrue(Res.getResourceBundle().containsKey(account.getMessageForSeller()));
            assertTrue(Res.getResourceBundle().containsKey("payment.stablecoin.info.account"));
        }
    }

    @Test
    public void accountOfAnotherPaymentMethodIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new StableCoinAccount(paymentMethod(PaymentMethod.SEPA_ID)));
    }

    @Test
    public void accountSurvivesProtobufRoundTrip() {
        StableCoinAccount account = newAccount(StableCoinPaymentRail.USDT_TRC20, TRON_ADDRESS);

        PaymentAccount restored = PaymentAccount.fromProto(account.toProtoMessage(), new CoreProtoResolver());

        assertNotNull(restored);
        assertTrue(restored instanceof StableCoinAccount);
        assertEquals(account, restored);
        assertEquals(TRON_ADDRESS, ((StableCoinAccount) restored).getAddress());
        assertEquals(StableCoinPaymentRail.USDT_TRC20.getStableCoin(), ((StableCoinAccount) restored).getStableCoin());
        assertEquals(List.of(new FiatCurrency("USD")), restored.getTradeCurrencies());
        assertEquals(account.getPaymentAccountPayload(), restored.getPaymentAccountPayload());
    }

    @Test
    public void accountAgeWitnessInputDependsOnRailAndAddress() {
        StableCoinAccountPayload tron = (StableCoinAccountPayload) newAccount(StableCoinPaymentRail.USDT_TRC20,
                TRON_ADDRESS).getPaymentAccountPayload();
        assertEquals("USDT_TRC20" + TRON_ADDRESS, new String(tron.getAgeWitnessInputData(),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    // The network of a coin is part of the payment method, so an account of the same coin on another network must
    // not match an offer.
    @Test
    public void accountMatchesOnlyAnOfferOfItsRail() {
        StableCoinAccount tronAccount = newAccount(StableCoinPaymentRail.USDT_TRC20, TRON_ADDRESS);
        StableCoinAccount ethereumAccount = newAccount(StableCoinPaymentRail.USDT_ERC20,
                "0xdAC17F958D2ee523a2206206994597C13D831ec7");

        Offer tronOffer = offer("USD", "USDT_TRC20");
        assertTrue(PaymentAccountUtil.isPaymentAccountValidForOffer(tronOffer, tronAccount));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(tronOffer, ethereumAccount));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("EUR", "USDT_TRC20"), tronAccount));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("USD", PaymentMethod.CELPAY_ID), tronAccount));
    }

    // An address on Ethereum is also an address on BNB Smart Chain, so payment account data of another rail must be
    // told apart, as well as stablecoin data for another payment method
    @Test
    public void paymentAccountDataOfAnotherPaymentMethodIsRecognized() {
        PaymentMethod usdtErc20 = PaymentMethod.getPaymentMethod("USDT_ERC20");
        StableCoinAccount ethereumAccount = newAccount(StableCoinPaymentRail.USDT_ERC20,
                "0xdAC17F958D2ee523a2206206994597C13D831ec7");
        StableCoinAccount bnbSmartChainAccount = newAccount(StableCoinPaymentRail.USDT_BEP20,
                "0xdAC17F958D2ee523a2206206994597C13D831ec7");
        PaymentMethod celPay = paymentMethod(PaymentMethod.CELPAY_ID);
        PaymentAccount celPayAccount = PaymentAccountFactory.getPaymentAccount(celPay);
        celPayAccount.init();

        assertFalse(StableCoinAccountPayload.isOfAnotherPaymentMethod(usdtErc20,
                ethereumAccount.getPaymentAccountPayload()));
        assertTrue(StableCoinAccountPayload.isOfAnotherPaymentMethod(usdtErc20,
                bnbSmartChainAccount.getPaymentAccountPayload()));
        assertTrue(StableCoinAccountPayload.isOfAnotherPaymentMethod(usdtErc20,
                celPayAccount.getPaymentAccountPayload()));
        assertTrue(StableCoinAccountPayload.isOfAnotherPaymentMethod(celPay,
                ethereumAccount.getPaymentAccountPayload()));
        // Stablecoin data with an ID which is no rail
        assertTrue(StableCoinAccountPayload.isOfAnotherPaymentMethod(usdtErc20,
                new StableCoinAccountPayload("NOT_LISTED_ERC20", "id")));
        assertTrue(StableCoinAccountPayload.isOfAnotherPaymentMethod(PaymentMethod.getDummyPaymentMethod("NOT_LISTED_ERC20"),
                new StableCoinAccountPayload("NOT_LISTED_ERC20", "id")));
        // Not a stablecoin trade at all
        assertFalse(StableCoinAccountPayload.isOfAnotherPaymentMethod(celPay,
                celPayAccount.getPaymentAccountPayload()));
    }

    // Not the static fields of PaymentMethod: ReceiptPredicatesTest replaces some of them by mocks
    private static PaymentMethod paymentMethod(String id) {
        return PaymentMethod.getActivePaymentMethod(id).orElseThrow();
    }

    private static StableCoinAccount newAccount(StableCoinPaymentRail rail, String address) {
        StableCoinAccount account = (StableCoinAccount) PaymentAccountFactory.getPaymentAccount(
                PaymentMethod.getPaymentMethod(rail.name()));
        account.init();
        account.setAccountName(rail.name() + " account");
        account.setAddress(address);
        return account;
    }

    private static Offer offer(String currencyCode, String paymentMethodId) {
        Offer offer = mock(Offer.class);
        when(offer.getCurrencyCode()).thenReturn(currencyCode);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(paymentMethodId));
        return offer;
    }
}
