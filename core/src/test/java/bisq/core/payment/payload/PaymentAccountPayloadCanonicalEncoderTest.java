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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment.payload;

import bisq.core.proto.CoreProtoResolver;

import bisq.common.crypto.Hash;
import bisq.common.encoding.canonical.CanonicalEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.HOLDER_NAME;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.RESERVED_0;
import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.SALT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SuppressWarnings("deprecation")
public class PaymentAccountPayloadCanonicalEncoderTest {
    private static final CoreProtoResolver CORE_PROTO_RESOLVER = new CoreProtoResolver();

    @Test
    public void canonicalBytesMatchProtobufBytesForEveryPaymentAccountPayload() {
        paymentAccountPayloadProtos().forEach(testCase -> {
            PaymentAccountPayload paymentAccountPayload = CORE_PROTO_RESOLVER.fromProto(testCase.proto());
            byte[] protobufBytes = paymentAccountPayload.toProtoMessage().toByteArray();

            assertArrayEquals(protobufBytes,
                    PaymentAccountPayloadCanonicalSchemas.encode(paymentAccountPayload, CanonicalEncoder.DEFAULT),
                    testCase.name() + " canonical bytes");
            assertArrayEquals(protobufBytes,
                    paymentAccountPayload.encodeCanonical(),
                    testCase.name() + " encodeCanonical bytes");
            assertArrayEquals(Hash.getRipemd160hash(protobufBytes),
                    paymentAccountPayload.getHashForContract(),
                    testCase.name() + " contract hash");
        });
    }

    @Test
    public void canonicalBytesMatchProtobufBytesWhenBankFieldsAreNull() {
        // fromProto converts empty bank-level strings to null for BankAccountPayload subclasses and
        // CashDepositAccountPayload. The canonical schema must skip null lambda returns the same way
        // protobuf skips unset strings, so the byte streams stay identical.
        nullableFieldProtos().forEach(testCase -> {
            PaymentAccountPayload paymentAccountPayload = CORE_PROTO_RESOLVER.fromProto(testCase.proto());
            byte[] protobufBytes = paymentAccountPayload.toProtoMessage().toByteArray();

            assertArrayEquals(protobufBytes,
                    PaymentAccountPayloadCanonicalSchemas.encode(paymentAccountPayload, CanonicalEncoder.DEFAULT),
                    testCase.name() + " canonical bytes");
            assertArrayEquals(protobufBytes,
                    paymentAccountPayload.encodeCanonical(),
                    testCase.name() + " encodeCanonical bytes");
            assertArrayEquals(Hash.getRipemd160hash(protobufBytes),
                    paymentAccountPayload.getHashForContract(),
                    testCase.name() + " contract hash");
        });
    }

    @Test
    public void canonicalBytesMatchProtobufBytesForLocallyCreatedPayloads() {
        // The existing matrix decodes every payload through fromProto, which always constructs
        // PaymentAccountPayloadExcludeFromJsonMap with preserveInsertionOrder=true, hides the
        // PaymentAccountPayload constructor's auto-salt logic and never exercises the default
        // maxTradePeriod=-1. Build the payloads via the public Java constructors so the
        // canonical schema is exercised against the LEGACY_HASHMAP_ORDER reorder and the
        // signed-int64 default-trade-period path that producers actually emit on the wire.
        locallyCreatedPayloads().forEach(named -> {
            PaymentAccountPayload paymentAccountPayload = named.payload();
            byte[] protobufBytes = paymentAccountPayload.toProtoMessage().toByteArray();

            assertArrayEquals(protobufBytes,
                    PaymentAccountPayloadCanonicalSchemas.encode(paymentAccountPayload, CanonicalEncoder.DEFAULT),
                    named.name() + " canonical bytes");
            assertArrayEquals(protobufBytes,
                    paymentAccountPayload.encodeCanonical(),
                    named.name() + " encodeCanonical bytes");
            assertArrayEquals(Hash.getRipemd160hash(protobufBytes),
                    paymentAccountPayload.getHashForContract(),
                    named.name() + " contract hash");
        });
    }

    private static List<NamedPayload> locallyCreatedPayloads() {
        AliPayAccountPayload aliPayWithSaltOnly = new AliPayAccountPayload(PaymentMethod.ALI_PAY_ID, "local-ali-1");
        aliPayWithSaltOnly.setAccountNr("local-ali-account");

        AliPayAccountPayload aliPayWithHolderName = new AliPayAccountPayload(PaymentMethod.ALI_PAY_ID, "local-ali-2");
        aliPayWithHolderName.setAccountNr("local-ali-account-2");
        aliPayWithHolderName.setHolderName("Local Holder");

        NationalBankAccountPayload nationalBankDefaults = new NationalBankAccountPayload(PaymentMethod.NATIONAL_BANK_ID,
                "local-national-bank");
        nationalBankDefaults.setCountryCode("US");
        nationalBankDefaults.setHolderName("Bank Holder");

        SepaAccountPayload sepa = new SepaAccountPayload(PaymentMethod.SEPA_ID,
                "local-sepa",
                List.of());
        sepa.setCountryCode("DE");
        sepa.setHolderName("Sepa Holder");
        sepa.setIban("DE89370400440532013000");
        sepa.setBic("COBADEFFXXX");

        return List.of(
                new NamedPayload("aliPaySaltOnly", aliPayWithSaltOnly),
                new NamedPayload("aliPayWithHolderName", aliPayWithHolderName),
                new NamedPayload("nationalBankDefaults", nationalBankDefaults),
                new NamedPayload("sepaWithEmptyAcceptedCountries", sepa));
    }

    private record NamedPayload(String name, PaymentAccountPayload payload) {
    }

    private static List<TestCase> nullableFieldProtos() {
        return List.of(
                testCase("nationalBankAllOptionalFieldsEmpty", countryBased(PaymentMethod.NATIONAL_BANK_ID,
                        country()
                                .setBankAccountPayload(protobuf.BankAccountPayload.newBuilder()
                                        .setHolderName("Only Required Holder")
                                        .setNationalBankAccountPayload(protobuf.NationalBankAccountPayload.newBuilder())))),
                testCase("specificBanksOptionalFieldsEmpty", countryBased(PaymentMethod.SPECIFIC_BANKS_ID,
                        country()
                                .setBankAccountPayload(protobuf.BankAccountPayload.newBuilder()
                                        .setHolderName("Specific Holder")
                                        .setSpecificBanksAccountPayload(protobuf.SpecificBanksAccountPayload.newBuilder())))),
                testCase("cashDepositOptionalFieldsEmpty", countryBased(PaymentMethod.CASH_DEPOSIT_ID,
                        country()
                                .setCashDepositAccountPayload(protobuf.CashDepositAccountPayload.newBuilder()
                                        .setHolderName("Cash Holder")))),
                testCase("sepaWithoutDeprecatedEmail", countryBased(PaymentMethod.SEPA_ID,
                        country()
                                .setSepaAccountPayload(protobuf.SepaAccountPayload.newBuilder()
                                        .setHolderName("Sepa Holder")
                                        .setIban("DE89370400440532013000")
                                        .setBic("COBADEFFXXX")
                                        .addAllAcceptedCountryCodes(List.of("DE", "FR"))))),
                testCase("sepaInstantWithoutAcceptedCountries", countryBased(PaymentMethod.SEPA_INSTANT_ID,
                        country()
                                .setSepaInstantAccountPayload(protobuf.SepaInstantAccountPayload.newBuilder()
                                        .setHolderName("Instant Holder")
                                        .setIban("FR1420041010050500013M02606")
                                        .setBic("PSSTFRPPXXX"))))
        );
    }

    private static List<TestCase> paymentAccountPayloadProtos() {
        return List.of(
                testCase("aliPay", account(PaymentMethod.ALI_PAY_ID)
                        .setAliPayAccountPayload(protobuf.AliPayAccountPayload.newBuilder()
                                .setAccountNr("ali-account"))
                        .build()),
                testCase("chaseQuickPay", account(PaymentMethod.CHASE_QUICK_PAY_ID)
                        .setChaseQuickPayAccountPayload(protobuf.ChaseQuickPayAccountPayload.newBuilder()
                                .setEmail("chase@example.com")
                                .setHolderName("Chase Holder"))
                        .build()),
                testCase("clearXchange", account(PaymentMethod.CLEAR_X_CHANGE_ID)
                        .setClearXchangeAccountPayload(protobuf.ClearXchangeAccountPayload.newBuilder()
                                .setHolderName("Clear Holder")
                                .setEmailOrMobileNr("clear@example.com"))
                        .build()),
                testCase("nationalBank", countryBased(PaymentMethod.NATIONAL_BANK_ID,
                        country()
                                .setBankAccountPayload(bank()
                                        .setNationalBankAccountPayload(protobuf.NationalBankAccountPayload.newBuilder())))),
                testCase("sameBank", countryBased(PaymentMethod.SAME_BANK_ID,
                        country()
                                .setBankAccountPayload(bank()
                                        .setSameBankAccontPayload(protobuf.SameBankAccountPayload.newBuilder())))),
                testCase("specificBanks", countryBased(PaymentMethod.SPECIFIC_BANKS_ID,
                        country()
                                .setBankAccountPayload(bank()
                                        .setSpecificBanksAccountPayload(protobuf.SpecificBanksAccountPayload.newBuilder()
                                                .addAllAcceptedBanks(List.of("Bank A", "Bank B")))))),
                testCase("achTransfer", countryBased(PaymentMethod.ACH_TRANSFER_ID,
                        country()
                                .setBankAccountPayload(bank()
                                        .setAchTransferAccountPayload(protobuf.AchTransferAccountPayload.newBuilder()
                                                .setHolderAddress("123 ACH Street"))))),
                testCase("domesticWireTransfer", countryBased(PaymentMethod.DOMESTIC_WIRE_TRANSFER_ID,
                        country()
                                .setBankAccountPayload(bank()
                                        .setDomesticWireTransferAccountPayload(protobuf.DomesticWireTransferAccountPayload.newBuilder()
                                                .setHolderAddress("456 Wire Avenue"))))),
                testCase("cashDeposit", countryBased(PaymentMethod.CASH_DEPOSIT_ID,
                        country()
                                .setCashDepositAccountPayload(protobuf.CashDepositAccountPayload.newBuilder()
                                        .setHolderName("Cash Holder")
                                        .setHolderEmail("cash@example.com")
                                        .setBankName("Cash Bank")
                                        .setBankId("cash-bank-id")
                                        .setBranchId("cash-branch")
                                        .setAccountNr("cash-account")
                                        .setAccountType("checking")
                                        .setRequirements("bring receipt")
                                        .setHolderTaxId("cash-tax")
                                        .setNationalAccountId("cash-national")))),
                testCase("sepa", countryBased(PaymentMethod.SEPA_ID,
                        country()
                                .setSepaAccountPayload(protobuf.SepaAccountPayload.newBuilder()
                                        .setHolderName("Sepa Holder")
                                        .setIban("DE89370400440532013000")
                                        .setBic("COBADEFFXXX")
                                        .setEmail("legacy-sepa@example.com")
                                        .addAllAcceptedCountryCodes(List.of("AT", "DE", "FR"))))),
                testCase("westernUnion", countryBased(PaymentMethod.WESTERN_UNION_ID,
                        country()
                                .setWesternUnionAccountPayload(protobuf.WesternUnionAccountPayload.newBuilder()
                                        .setHolderName("Western Holder")
                                        .setCity("Denver")
                                        .setState("CO")
                                        .setEmail("western@example.com")))),
                testCase("sepaInstant", countryBased(PaymentMethod.SEPA_INSTANT_ID,
                        country()
                                .setSepaInstantAccountPayload(protobuf.SepaInstantAccountPayload.newBuilder()
                                        .setHolderName("Instant Holder")
                                        .setIban("FR1420041010050500013M02606")
                                        .setBic("PSSTFRPPXXX")
                                        .addAllAcceptedCountryCodes(List.of("BE", "FR"))))),
                testCase("f2f", countryBased(PaymentMethod.F2F_ID,
                        country()
                                .setF2FAccountPayload(protobuf.F2FAccountPayload.newBuilder()
                                        .setContact("f2f-contact")
                                        .setCity("Lisbon")
                                        .setExtraInfo("meet inside")))),
                testCase("upi", countryBased(PaymentMethod.UPI_ID,
                        country()
                                .setUpiAccountPayload(protobuf.UpiAccountPayload.newBuilder()
                                        .setVirtualPaymentAddress("holder@upi")))),
                testCase("paytm", countryBased(PaymentMethod.PAYTM_ID,
                        country()
                                .setPaytmAccountPayload(protobuf.PaytmAccountPayload.newBuilder()
                                        .setEmailOrMobileNr("paytm@example.com")))),
                testCase("neft", countryBased(PaymentMethod.NEFT_ID,
                        country()
                                .setIfscBasedAccountPayload(ifsc()
                                        .setNeftAccountPayload(protobuf.NeftAccountPayload.newBuilder())))),
                testCase("rtgs", countryBased(PaymentMethod.RTGS_ID,
                        country()
                                .setIfscBasedAccountPayload(ifsc()
                                        .setRtgsAccountPayload(protobuf.RtgsAccountPayload.newBuilder())))),
                testCase("imps", countryBased(PaymentMethod.IMPS_ID,
                        country()
                                .setIfscBasedAccountPayload(ifsc()
                                        .setImpsAccountPayload(protobuf.ImpsAccountPayload.newBuilder())))),
                testCase("nequi", countryBased(PaymentMethod.NEQUI_ID,
                        country()
                                .setNequiAccountPayload(protobuf.NequiAccountPayload.newBuilder()
                                        .setMobileNr("+57123456789")))),
                testCase("bizum", countryBased(PaymentMethod.BIZUM_ID,
                        country()
                                .setBizumAccountPayload(protobuf.BizumAccountPayload.newBuilder()
                                        .setMobileNr("+34123456789")))),
                testCase("pix", countryBased(PaymentMethod.PIX_ID,
                        country()
                                .setPixAccountPayload(protobuf.PixAccountPayload.newBuilder()
                                        .setPixKey("pix-key")))),
                testCase("satispay", countryBased(PaymentMethod.SATISPAY_ID,
                        country()
                                .setSatispayAccountPayload(protobuf.SatispayAccountPayload.newBuilder()
                                        .setMobileNr("+39123456789")
                                        .setHolderName("Satispay Holder")))),
                testCase("strike", countryBased(PaymentMethod.STRIKE_ID,
                        country()
                                .setStrikeAccountPayload(protobuf.StrikeAccountPayload.newBuilder()
                                        .setHolderName("Strike Holder")))),
                testCase("tikkie", countryBased(PaymentMethod.TIKKIE_ID,
                        country()
                                .setTikkieAccountPayload(protobuf.TikkieAccountPayload.newBuilder()
                                        .setIban("NL91ABNA0417164300")))),
                testCase("transferwiseUsd", countryBased(PaymentMethod.TRANSFERWISE_USD_ID,
                        country()
                                .setTransferwiseUsdAccountPayload(protobuf.TransferwiseUsdAccountPayload.newBuilder()
                                        .setEmail("wise-usd@example.com")
                                        .setHolderName("Wise USD Holder")
                                        .setBeneficiaryAddress("789 Wise Road")))),
                testCase("mercadoPago", countryBased(PaymentMethod.MERCADO_PAGO_ID,
                        country()
                                .setMercadoPagoAccountPayload(protobuf.MercadoPagoAccountPayload.newBuilder()
                                        .setHolderName("Mercado Holder")
                                        .setHolderId("mercado-id")))),
                testCase("cryptoCurrency", account(PaymentMethod.BLOCK_CHAINS_ID)
                        .setCryptoCurrencyAccountPayload(protobuf.CryptoCurrencyAccountPayload.newBuilder()
                                .setAddress("bc1qcryptoaddress"))
                        .build()),
                testCase("fasterPayments", account(PaymentMethod.FASTER_PAYMENTS_ID)
                        .setFasterPaymentsAccountPayload(protobuf.FasterPaymentsAccountPayload.newBuilder()
                                .setSortCode("010203")
                                .setAccountNr("12345678")
                                .setEmail("legacy-faster@example.com"))
                        .build()),
                testCase("interacETransfer", account(PaymentMethod.INTERAC_E_TRANSFER_ID)
                        .setInteracETransferAccountPayload(protobuf.InteracETransferAccountPayload.newBuilder()
                                .setEmail("interac@example.com")
                                .setHolderName("Interac Holder")
                                .setQuestion("Question?")
                                .setAnswer("Answer"))
                        .build()),
                testCase("okPay", account(PaymentMethod.OK_PAY_ID)
                        .setOKPayAccountPayload(protobuf.OKPayAccountPayload.newBuilder()
                                .setAccountNr("okpay-account"))
                        .build()),
                testCase("perfectMoney", account(PaymentMethod.PERFECT_MONEY_ID)
                        .setPerfectMoneyAccountPayload(protobuf.PerfectMoneyAccountPayload.newBuilder()
                                .setAccountNr("perfect-account"))
                        .build()),
                testCase("swish", account(PaymentMethod.SWISH_ID)
                        .setSwishAccountPayload(protobuf.SwishAccountPayload.newBuilder()
                                .setMobileNr("+46123456789")
                                .setHolderName("Swish Holder"))
                        .build()),
                testCase("usPostalMoneyOrder", account(PaymentMethod.US_POSTAL_MONEY_ORDER_ID)
                        .setUSPostalMoneyOrderAccountPayload(protobuf.USPostalMoneyOrderAccountPayload.newBuilder()
                                .setPostalAddress("PO Box 123")
                                .setHolderName("Postal Holder"))
                        .build()),
                testCase("uphold", account(PaymentMethod.UPHOLD_ID)
                        .setUpholdAccountPayload(protobuf.UpholdAccountPayload.newBuilder()
                                .setAccountId("uphold-account")
                                .setAccountOwner("Uphold Owner"))
                        .build()),
                testCase("cashApp", account(PaymentMethod.CASH_APP_ID)
                        .setCashAppAccountPayload(protobuf.CashAppAccountPayload.newBuilder()
                                .setCashTag("$cashtag"))
                        .build()),
                testCase("moneyBeam", account(PaymentMethod.MONEY_BEAM_ID)
                        .setMoneyBeamAccountPayload(protobuf.MoneyBeamAccountPayload.newBuilder()
                                .setAccountId("money-beam-account"))
                        .build()),
                testCase("venmo", account(PaymentMethod.VENMO_ID)
                        .setVenmoAccountPayload(protobuf.VenmoAccountPayload.newBuilder()
                                .setVenmoUserName("venmo-user")
                                .setHolderName("Venmo Holder"))
                        .build()),
                testCase("popmoney", account(PaymentMethod.POPMONEY_ID)
                        .setPopmoneyAccountPayload(protobuf.PopmoneyAccountPayload.newBuilder()
                                .setAccountId("popmoney-account")
                                .setHolderName("Popmoney Holder"))
                        .build()),
                testCase("revolut", account(PaymentMethod.REVOLUT_ID)
                        .setRevolutAccountPayload(protobuf.RevolutAccountPayload.newBuilder()
                                .setAccountId("revolut-account")
                                .setUserName("revolut-user"))
                        .build()),
                testCase("weChatPay", account(PaymentMethod.WECHAT_PAY_ID)
                        .setWeChatPayAccountPayload(protobuf.WeChatPayAccountPayload.newBuilder()
                                .setAccountNr("wechat-account"))
                        .build()),
                testCase("moneyGram", account(PaymentMethod.MONEY_GRAM_ID)
                        .setMoneyGramAccountPayload(protobuf.MoneyGramAccountPayload.newBuilder()
                                .setHolderName("MoneyGram Holder")
                                .setCountryCode("US")
                                .setState("CA")
                                .setEmail("moneygram@example.com"))
                        .build()),
                testCase("halCash", account(PaymentMethod.HAL_CASH_ID)
                        .setHalCashAccountPayload(protobuf.HalCashAccountPayload.newBuilder()
                                .setMobileNr("+34111222333"))
                        .build()),
                testCase("promptPay", account(PaymentMethod.PROMPT_PAY_ID)
                        .setPromptPayAccountPayload(protobuf.PromptPayAccountPayload.newBuilder()
                                .setPromptPayId("prompt-pay-id"))
                        .build()),
                testCase("advancedCash", account(PaymentMethod.ADVANCED_CASH_ID)
                        .setAdvancedCashAccountPayload(protobuf.AdvancedCashAccountPayload.newBuilder()
                                .setAccountNr("advanced-cash-account"))
                        .build()),
                testCase("instantCryptoCurrency", account(PaymentMethod.BLOCK_CHAINS_INSTANT_ID)
                        .setInstantCryptoCurrencyAccountPayload(protobuf.InstantCryptoCurrencyAccountPayload.newBuilder()
                                .setAddress("bc1qinstantcryptoaddress"))
                        .build()),
                testCase("japanBank", account(PaymentMethod.JAPAN_BANK_ID)
                        .setJapanBankAccountPayload(protobuf.JapanBankAccountPayload.newBuilder()
                                .setBankName("Japan Bank")
                                .setBankCode("0001")
                                .setBankBranchName("Tokyo")
                                .setBankBranchCode("123")
                                .setBankAccountType("ordinary")
                                .setBankAccountName("Japan Holder")
                                .setBankAccountNumber("1234567"))
                        .build()),
                testCase("transferwise", account(PaymentMethod.TRANSFERWISE_ID)
                        .setTransferwiseAccountPayload(protobuf.TransferwiseAccountPayload.newBuilder()
                                .setEmail("wise@example.com"))
                        .build()),
                testCase("australiaPayid", account(PaymentMethod.AUSTRALIA_PAYID_ID)
                        .setAustraliaPayidPayload(protobuf.AustraliaPayidPayload.newBuilder()
                                .setBankAccountName("PayID Holder")
                                .setPayid("payid@example.com"))
                        .build()),
                testCase("amazonGiftCard", account(PaymentMethod.AMAZON_GIFT_CARD_ID)
                        .setAmazonGiftCardAccountPayload(protobuf.AmazonGiftCardAccountPayload.newBuilder()
                                .setEmailOrMobileNr("amazon@example.com")
                                .setCountryCode("US"))
                        .build()),
                testCase("cashByMail", account(PaymentMethod.CASH_BY_MAIL_ID)
                        .setCashByMailAccountPayload(protobuf.CashByMailAccountPayload.newBuilder()
                                .setPostalAddress("Mailbox 99")
                                .setContact("mail contact")
                                .setExtraInfo("double envelope"))
                        .build()),
                testCase("capitual", account(PaymentMethod.CAPITUAL_ID)
                        .setCapitualAccountPayload(protobuf.CapitualAccountPayload.newBuilder()
                                .setAccountNr("capitual-account"))
                        .build()),
                testCase("paysera", account(PaymentMethod.PAYSERA_ID)
                        .setPayseraAccountPayload(protobuf.PayseraAccountPayload.newBuilder()
                                .setEmail("paysera@example.com"))
                        .build()),
                testCase("paxum", account(PaymentMethod.PAXUM_ID)
                        .setPaxumAccountPayload(protobuf.PaxumAccountPayload.newBuilder()
                                .setEmail("paxum@example.com"))
                        .build()),
                testCase("swift", account(PaymentMethod.SWIFT_ID)
                        .setSwiftAccountPayload(protobuf.SwiftAccountPayload.newBuilder()
                                .setBeneficiaryName("Swift Beneficiary")
                                .setBeneficiaryAccountNr("swift-account")
                                .setBeneficiaryAddress("Swift Address")
                                .setBeneficiaryCity("Swift City")
                                .setBeneficiaryPhone("+10000000000")
                                .setSpecialInstructions("swift instructions")
                                .setBankSwiftCode("ABCDEFGH")
                                .setBankCountryCode("GB")
                                .setBankName("Swift Bank")
                                .setBankBranch("Main")
                                .setBankAddress("Bank Address")
                                .setIntermediarySwiftCode("HGFEDCBA")
                                .setIntermediaryCountryCode("US")
                                .setIntermediaryName("Intermediary Bank")
                                .setIntermediaryBranch("Intermediary Branch")
                                .setIntermediaryAddress("Intermediary Address"))
                        .build()),
                testCase("celPay", account(PaymentMethod.CELPAY_ID)
                        .setCelPayAccountPayload(protobuf.CelPayAccountPayload.newBuilder()
                                .setEmail("celpay@example.com"))
                        .build()),
                testCase("monese", account(PaymentMethod.MONESE_ID)
                        .setMoneseAccountPayload(protobuf.MoneseAccountPayload.newBuilder()
                                .setMobileNr("+44123456789")
                                .setHolderName("Monese Holder"))
                        .build()),
                testCase("verse", account(PaymentMethod.VERSE_ID)
                        .setVerseAccountPayload(protobuf.VerseAccountPayload.newBuilder()
                                .setHolderName("Verse Holder"))
                        .build()),
                testCase("bsqSwap", account(PaymentMethod.BSQ_SWAP_ID)
                        .setBsqSwapAccountPayload(protobuf.BsqSwapAccountPayload.newBuilder())
                        .build()),
                testCase("sbp", account(PaymentMethod.SBP_ID)
                        .setSbpAccountPayload(protobuf.SbpAccountPayload.newBuilder()
                                .setHolderName("Sbp Holder")
                                .setMobileNumber("+79123456789")
                                .setBankName("Sbp Bank"))
                        .build())
        );
    }

    private static protobuf.PaymentAccountPayload.Builder account(String paymentMethodId) {
        return protobuf.PaymentAccountPayload.newBuilder()
                .setId("account-" + paymentMethodId)
                .setPaymentMethodId(paymentMethodId)
                .setMaxTradePeriod(12_345)
                .putAllExcludeFromJsonData(excludeFromJsonData());
    }

    private static protobuf.PaymentAccountPayload countryBased(String paymentMethodId,
                                                               protobuf.CountryBasedPaymentAccountPayload.Builder builder) {
        return account(paymentMethodId)
                .setCountryBasedPaymentAccountPayload(builder)
                .build();
    }

    private static protobuf.CountryBasedPaymentAccountPayload.Builder country() {
        return protobuf.CountryBasedPaymentAccountPayload.newBuilder()
                .setCountryCode("US");
    }

    private static protobuf.BankAccountPayload.Builder bank() {
        return protobuf.BankAccountPayload.newBuilder()
                .setHolderName("Bank Holder")
                .setBankName("Bank Name")
                .setBankId("bank-id")
                .setBranchId("branch-id")
                .setAccountNr("account-nr")
                .setAccountType("checking")
                .setHolderTaxId("tax-id")
                .setNationalAccountId("national-account-id");
    }

    private static protobuf.IfscBasedAccountPayload.Builder ifsc() {
        return protobuf.IfscBasedAccountPayload.newBuilder()
                .setHolderName("Ifsc Holder")
                .setAccountNr("ifsc-account-nr")
                .setIfsc("IFSC0001");
    }

    private static Map<String, String> excludeFromJsonData() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(HOLDER_NAME, "Excluded Holder");
        map.put(SALT, "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        map.put(RESERVED_0, "reserved-value");
        return map;
    }

    private static TestCase testCase(String name, protobuf.PaymentAccountPayload proto) {
        return new TestCase(name, proto);
    }

    private record TestCase(String name, protobuf.PaymentAccountPayload proto) {
    }
}
