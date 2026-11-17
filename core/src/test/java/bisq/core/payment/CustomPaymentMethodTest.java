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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.api.model.PaymentAccountForm;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap;
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.statistics.ReferralIdService;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import org.bitcoinj.core.Coin;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.CUSTOM_PAYMENT_METHOD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CustomPaymentMethodTest {
    // Other tests replace static fields of PaymentMethod with mocks, so the payment method is taken from the list
    private static final PaymentMethod CUSTOM = PaymentMethod.getPaymentMethod(PaymentMethod.CUSTOM_ID);

    @BeforeAll
    public static void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Payment method
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void thereIsOneCustomPaymentMethod() {
        assertEquals("CUSTOM", PaymentMethod.CUSTOM_ID);
        assertTrue(PaymentMethod.getActivePaymentMethod(PaymentMethod.CUSTOM_ID).isPresent());
        assertEquals(1, PaymentMethod.getPaymentMethods().stream().filter(PaymentMethod::isCustom).count());
        assertTrue(CUSTOM.isCustom());
        assertFalse(PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID).isCustom());
        assertFalse(PaymentMethod.getPaymentMethod(PaymentMethod.F2F_ID).isCustom());
        assertFalse(PaymentMethod.getPaymentMethod("NOT_LISTED").isCustom());
    }

    @Test
    public void customPaymentMethodIsAFiatPaymentMethod() {
        assertTrue(CUSTOM.isFiat());
        assertFalse(CUSTOM.isAltcoin());
        assertFalse(CUSTOM.isBlockchain());
    }

    // Account signing would never start for a payment method which only few traders use, so the trade limit is a
    // low one instead (see CustomPaymentMethodTradeLimitTest)
    @Test
    public void customPaymentMethodIsNotInTheListWithChargebackRisk() {
        assertFalse(PaymentMethod.hasChargebackRisk(CUSTOM, "USD"));
        assertFalse(PaymentMethod.hasChargebackRisk(CUSTOM, "EUR"));
        assertFalse(PaymentMethod.hasChargebackRisk(CUSTOM));
    }

    @Test
    public void tradePeriodIsFourDays() {
        assertEquals(TimeUnit.DAYS.toMillis(4), CUSTOM.getMaxTradePeriod());
    }

    @Test
    public void everyFiatCurrencyIsSupported() {
        assertEquals(CurrencyUtil.getAllFiatCurrencies(), PaymentAccountUtil.getTradeCurrencies(CUSTOM));
        assertTrue(PaymentAccountUtil.supportsCurrency(CUSTOM, new FiatCurrency("USD")));
        assertTrue(PaymentAccountUtil.supportsCurrency(CUSTOM, new FiatCurrency("KES")));
        assertTrue(PaymentAccountUtil.supportsCurrency(CUSTOM, new FiatCurrency("XOF")));
    }

    // Many views show a payment method with Res.get(id) and Res.get(id + "_SHORT")
    @Test
    public void displayStringsExist() {
        assertEquals("Custom payment method", CUSTOM.getDisplayString());
        assertEquals("Custom", CUSTOM.getShortName());
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");
        assertTrue(Res.getResourceBundle().containsKey(account.getMessageForBuyer()));
        assertTrue(Res.getResourceBundle().containsKey(account.getMessageForSeller()));
    }

    // A text with an argument is a message format, in which a single quote or a brace has a meaning
    @Test
    public void displayStringsWithArgumentsAreFormatted() {
        String info = Res.get("payment.custom.info", "0.05 BTC");
        assertTrue(info.contains("the trade limit is 0.05 BTC per trade"), info);
        assertFalse(info.contains("{0}"), info);

        assertTrue(Res.get("payment.custom.name.prompt", CustomPaymentMethodUtil.MAX_NAME_LENGTH)
                .contains("max. 20 characters"));
        assertFalse(Res.get("payment.custom.accountData.prompt", CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH)
                .contains("{0}"));
        assertTrue(Res.get("payment.custom.name.tooLong", CustomPaymentMethodUtil.MAX_NAME_LENGTH)
                .contains("20 characters"));
        assertEquals("Name of the payment method (text of the maker): M-Pesa",
                Res.get("payment.custom.offerbook.tooltip.name", "M-Pesa"));

        String buyer = Res.get("portfolio.pending.step2_buyer.custom", "1000 KES");
        assertTrue(buyer.startsWith("Please pay 1000 KES to the BTC seller"), buyer);
        String seller = Res.get("portfolio.pending.step3_seller.custom", "PART1 ", "1000 KES", "open a dispute.");
        assertTrue(seller.startsWith("PART1 Please check if you have received 1000 KES from the BTC buyer"), seller);
        assertTrue(seller.endsWith("If you are in doubt, open a dispute."), seller);
    }


    @Test
    public void nameOfTheCustomPaymentMethodItselfIsNoNameOfACustomPaymentMethod() {
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Custom"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Custom payment method"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Account
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void factoryCreatesTheAccountOfTheCustomPaymentMethod() {
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(CUSTOM);
        assertTrue(paymentAccount instanceof CustomPaymentMethodAccount);
        paymentAccount.init();

        assertEquals(CUSTOM, paymentAccount.getPaymentMethod());
        assertTrue(paymentAccount.getPaymentAccountPayload() instanceof CustomPaymentMethodAccountPayload);
        assertEquals(PaymentMethod.CUSTOM_ID, paymentAccount.getPaymentAccountPayload().getPaymentMethodId());
        assertEquals(CurrencyUtil.getAllFiatCurrencies(), paymentAccount.getSupportedCurrencies());
        assertEquals("", ((CustomPaymentMethodAccount) paymentAccount).getPaymentMethodName());
        assertEquals("", ((CustomPaymentMethodAccount) paymentAccount).getAccountData());
    }

    @Test
    public void accountSurvivesProtobufRoundTrip() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");

        PaymentAccount restored = PaymentAccount.fromProto(account.toProtoMessage(), new CoreProtoResolver());

        assertNotNull(restored);
        assertTrue(restored instanceof CustomPaymentMethodAccount);
        assertEquals(account, restored);
        assertEquals("M-Pesa", ((CustomPaymentMethodAccount) restored).getPaymentMethodName());
        assertEquals("Jane Doe\n+254 700 000000", ((CustomPaymentMethodAccount) restored).getAccountData());
        assertEquals(new FiatCurrency("KES"), restored.getSingleTradeCurrency());
        assertEquals(account.getPaymentAccountPayload(), restored.getPaymentAccountPayload());
    }

    // The same data as the user defined account of Bisq 2 uses: the payment method ID and the account data
    @Test
    public void accountAgeWitnessInputIsThePaymentMethodIdAndTheAccountData() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");

        assertEquals("CUSTOM" + "Jane Doe\n+254 700 000000",
                new String(account.getPaymentAccountPayload().getAgeWitnessInputData(), StandardCharsets.UTF_8));
    }

    @Test
    public void paymentDetailsShowTheNameAndTheAccountDataInOneLine() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");

        assertEquals("Custom payment method - Name of the payment method: M-Pesa, " +
                        "Account data: Jane Doe, +254 700 000000",
                account.getPaymentAccountPayload().getPaymentDetails());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void offerOfAnAccountCarriesTheName() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(accountAgeWitnessService.getMyWitnessHashAsHex(any())).thenReturn("00");
        ReferralIdService referralIdService = mock(ReferralIdService.class);
        when(referralIdService.getOptionalReferralId()).thenReturn(Optional.empty());
        OfferUtil offerUtil = new OfferUtil(accountAgeWitnessService, null, null, null, null, null, referralIdService,
                null);

        OfferPayloadExtraDataMap extraDataMap = offerUtil.getOfferPayloadExtraDataMap(newAccount("M-Pesa", "KES"),
                "KES",
                OfferDirection.SELL);

        assertNotNull(extraDataMap);
        assertEquals("M-Pesa", extraDataMap.get(CUSTOM_PAYMENT_METHOD_NAME));
        assertEquals("M-Pesa", offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa").getCustomPaymentMethodName());
    }

    @Test
    public void offerOfAnotherAccountCarriesNoName() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(accountAgeWitnessService.getMyWitnessHashAsHex(any())).thenReturn("00");
        ReferralIdService referralIdService = mock(ReferralIdService.class);
        when(referralIdService.getOptionalReferralId()).thenReturn(Optional.empty());
        OfferUtil offerUtil = new OfferUtil(accountAgeWitnessService, null, null, null, null, null, referralIdService,
                null);
        PaymentAccount celPayAccount = PaymentAccountFactory.getPaymentAccount(
                PaymentMethod.getPaymentMethod(PaymentMethod.CELPAY_ID));
        celPayAccount.init();

        OfferPayloadExtraDataMap extraDataMap = offerUtil.getOfferPayloadExtraDataMap(celPayAccount,
                "USD",
                OfferDirection.SELL);

        assertNotNull(extraDataMap);
        assertFalse(extraDataMap.containsKey(CUSTOM_PAYMENT_METHOD_NAME));
    }

    // An account which was imported has not passed the account form
    @Test
    public void noOfferIsCreatedForAnAccountWithoutAValidName() {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getAddress()).thenReturn(new NodeAddress("maker.onion", 9999));
        OfferUtil offerUtil = new OfferUtil(null, null, mock(FilterPolicyService.class), null, null, p2PService, null,
                null);
        double securityDeposit = Restrictions.getMinBuyerSecurityDepositAsPercent();

        offerUtil.validateOfferData(securityDeposit, newAccount("M-Pesa", "KES"), "KES", Coin.ZERO, List.of());

        for (String name : List.of("", "--", "M-Pesa\nSEPA", "a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH + 1))) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> offerUtil.validateOfferData(securityDeposit, newAccount(name, "KES"), "KES", Coin.ZERO,
                            List.of()));
            assertEquals("The name of the custom payment method of the account is not valid", exception.getMessage());
        }
    }

    // The peer refuses such account data in the trade, after the trade fee is paid
    @Test
    public void noOfferIsCreatedForAnAccountWithoutValidAccountData() {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getAddress()).thenReturn(new NodeAddress("maker.onion", 9999));
        OfferUtil offerUtil = new OfferUtil(null, null, mock(FilterPolicyService.class), null, null, p2PService, null,
                null);
        double securityDeposit = Restrictions.getMinBuyerSecurityDepositAsPercent();

        for (String accountData : List.of("", "---", "a".repeat(CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH + 1))) {
            CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");
            account.setAccountData(accountData);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> offerUtil.validateOfferData(securityDeposit, account, "KES", Coin.ZERO, List.of()));
            assertEquals("The account data of the account of the custom payment method is not valid",
                    exception.getMessage());
        }
    }

    // The name in an offer is a text of the maker. A name which breaks the rules is not shown and matches no account.
    @Test
    public void offerWithoutAValidNameHasNoName() {
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID, null).getCustomPaymentMethodName());
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID, "").getCustomPaymentMethodName());
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID, "--").getCustomPaymentMethodName());
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa\nfree BTC").getCustomPaymentMethodName());
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID, "abc\u202Edef").getCustomPaymentMethodName());
        assertEquals("", offer("KES", PaymentMethod.CUSTOM_ID,
                "a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH + 1)).getCustomPaymentMethodName());
    }

    // The prefix tells that the name is a text of the maker and no payment method of Bisq
    @Test
    public void offerShowsTheNameWithThePrefixOfTheCustomPaymentMethod() {
        Offer offer = offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa");
        assertEquals("Custom payment method: M-Pesa", offer.getPaymentMethodDisplayString());
        assertEquals("Custom: M-Pesa", offer.getPaymentMethodShortName());
        assertEquals("Custom: M-Pesa", offer.getPaymentMethodNameWithCountryCode());

        assertEquals("Custom: SEPA", offer("EUR", PaymentMethod.CUSTOM_ID, "SEPA").getPaymentMethodShortName());
    }

    @Test
    public void offerWithoutAValidNameShowsNoName() {
        Offer offer = offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa\nSEPA");
        assertEquals("Custom payment method: " + Res.get("shared.na"), offer.getPaymentMethodDisplayString());
        assertEquals("Custom: " + Res.get("shared.na"), offer.getPaymentMethodShortName());
    }

    @Test
    public void offerOfAnotherPaymentMethodShowsThePaymentMethod() {
        Offer offer = offer("USD", PaymentMethod.CELPAY_ID, "M-Pesa");
        assertEquals("CelPay", offer.getPaymentMethodDisplayString());
        assertEquals("CelPay", offer.getPaymentMethodShortName());
        assertEquals("CelPay", offer.getPaymentMethodNameWithCountryCode());
    }

    @Test
    public void offerOfAnotherPaymentMethodHasNoName() {
        assertEquals("", offer("USD", PaymentMethod.CELPAY_ID, "M-Pesa").getCustomPaymentMethodName());
        assertEquals("", offer("USD", "NOT_LISTED", "M-Pesa").getCustomPaymentMethodName());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Account for an offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // All custom payment methods share one payment method ID, so only the name tells them apart
    @Test
    public void accountMatchesOnlyAnOfferWithItsNameAndCurrency() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");

        assertTrue(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa"), account));
        assertTrue(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, "m pesa"), account));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, "Airtel Money"), account));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("UGX", PaymentMethod.CUSTOM_ID, "M-Pesa"), account));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CELPAY_ID, "M-Pesa"), account));
    }

    @Test
    public void accountDoesNotMatchAnOfferWithoutAValidName() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");

        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, null), account));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa\n"), account));
    }

    // An account which was imported has not passed the account form
    @Test
    public void accountWithoutValidAccountDataMatchesNoOffer() {
        CustomPaymentMethodAccount account = newAccount("M-Pesa", "KES");
        Offer offer = offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa");
        assertTrue(PaymentAccountUtil.isPaymentAccountValidForOffer(offer, account));

        account.setAccountData("");
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer, account));
        account.setAccountData("a".repeat(CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH + 1));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer, account));
    }

    @Test
    public void accountWithoutAValidNameMatchesNoOffer() {
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, "M-Pesa"),
                newAccount("", "KES")));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("KES", PaymentMethod.CUSTOM_ID, null),
                newAccount("", "KES")));
    }

    @Test
    public void accountOfAnotherPaymentMethodDoesNotMatchACustomOffer() {
        PaymentAccount celPayAccount = PaymentAccountFactory.getPaymentAccount(
                PaymentMethod.getPaymentMethod(PaymentMethod.CELPAY_ID));
        celPayAccount.init();
        celPayAccount.setSingleTradeCurrency(new FiatCurrency("USD"));

        assertTrue(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("USD", PaymentMethod.CELPAY_ID, null), celPayAccount));
        assertFalse(PaymentAccountUtil.isPaymentAccountValidForOffer(offer("USD", PaymentMethod.CUSTOM_ID, "CelPay"), celPayAccount));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The API cannot show the warnings of the custom payment method, and its form does not check the name
    @Test
    public void apiCreatesNoFormAndNoAccountOfTheCustomPaymentMethod() {
        PaymentAccountForm paymentAccountForm = new PaymentAccountForm();

        IllegalArgumentException formException = assertThrows(IllegalArgumentException.class,
                () -> paymentAccountForm.getPaymentAccountForm(PaymentMethod.CUSTOM_ID));
        assertEquals("the api does not support CUSTOM payment accounts", formException.getMessage());

        String json = "{\"paymentMethodId\": \"CUSTOM\", \"accountName\": \"x\", \"paymentMethodName\": \"SEPA\"}";
        IllegalArgumentException accountException = assertThrows(IllegalArgumentException.class,
                () -> paymentAccountForm.toPaymentAccount(json));
        assertEquals("the api does not support CUSTOM payment accounts", accountException.getMessage());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static CustomPaymentMethodAccount newAccount(String paymentMethodName, String currencyCode) {
        CustomPaymentMethodAccount account = (CustomPaymentMethodAccount) PaymentAccountFactory.getPaymentAccount(CUSTOM);
        account.init();
        account.setAccountName("Custom account");
        account.setPaymentMethodName(paymentMethodName);
        account.setAccountData("Jane Doe\n+254 700 000000");
        account.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        return account;
    }

    private static Offer offer(String currencyCode, String paymentMethodId, @Nullable String customPaymentMethodName) {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        if (customPaymentMethodName != null) {
            extraDataMap.put(CUSTOM_PAYMENT_METHOD_NAME, customPaymentMethodName);
        }
        return new Offer(new OfferPayload("1234",
                0L,
                null,
                null,
                OfferDirection.BUY,
                100000L,
                0.0,
                false,
                100000L,
                100000L,
                "BTC",
                currencyCode,
                null,
                null,
                paymentMethodId,
                "",
                null,
                null,
                null,
                null,
                null,
                "",
                0L,
                0L,
                0L,
                false,
                0L,
                0L,
                0L,
                0L,
                false,
                false,
                0L,
                0L,
                false,
                null,
                extraDataMap,
                0));
    }
}
