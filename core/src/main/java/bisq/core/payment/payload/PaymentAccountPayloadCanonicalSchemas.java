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

import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalMapEntryIterator;
import bisq.common.encoding.canonical.CanonicalSchema;

import java.util.List;

import javax.annotation.Nullable;

final class PaymentAccountPayloadCanonicalSchemas {
    private static final CanonicalMapEntryIterator<String, String> SOURCE_ITERATION_ORDER = List::iterator;

    private static final CanonicalSchema<AliPayAccountPayload> ALI_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<AliPayAccountPayload>newBuilder()
                    .string(1, AliPayAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<ChaseQuickPayAccountPayload> CHASE_QUICK_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<ChaseQuickPayAccountPayload>newBuilder()
                    .string(1, ChaseQuickPayAccountPayload::getEmail)
                    .string(2, ChaseQuickPayAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<ClearXchangeAccountPayload> CLEAR_XCHANGE_ACCOUNT_SCHEMA =
            CanonicalSchema.<ClearXchangeAccountPayload>newBuilder()
                    .string(1, ClearXchangeAccountPayload::getHolderName)
                    .string(2, ClearXchangeAccountPayload::getEmailOrMobileNr)
                    .build();
    private static final CanonicalSchema<CryptoCurrencyAccountPayload> CRYPTO_CURRENCY_ACCOUNT_SCHEMA =
            CanonicalSchema.<CryptoCurrencyAccountPayload>newBuilder()
                    .string(1, CryptoCurrencyAccountPayload::getAddress)
                    .build();
    private static final CanonicalSchema<FasterPaymentsAccountPayload> FASTER_PAYMENTS_ACCOUNT_SCHEMA =
            CanonicalSchema.<FasterPaymentsAccountPayload>newBuilder()
                    .string(1, FasterPaymentsAccountPayload::getSortCode)
                    .string(2, FasterPaymentsAccountPayload::getAccountNr)
                    .string(3, FasterPaymentsAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<InteracETransferAccountPayload> INTERAC_E_TRANSFER_ACCOUNT_SCHEMA =
            CanonicalSchema.<InteracETransferAccountPayload>newBuilder()
                    .string(1, InteracETransferAccountPayload::getEmail)
                    .string(2, InteracETransferAccountPayload::getHolderName)
                    .string(3, InteracETransferAccountPayload::getQuestion)
                    .string(4, InteracETransferAccountPayload::getAnswer)
                    .build();
    private static final CanonicalSchema<OKPayAccountPayload> OK_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<OKPayAccountPayload>newBuilder()
                    .string(1, OKPayAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<PerfectMoneyAccountPayload> PERFECT_MONEY_ACCOUNT_SCHEMA =
            CanonicalSchema.<PerfectMoneyAccountPayload>newBuilder()
                    .string(1, PerfectMoneyAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<SwishAccountPayload> SWISH_ACCOUNT_SCHEMA =
            CanonicalSchema.<SwishAccountPayload>newBuilder()
                    .string(1, SwishAccountPayload::getMobileNr)
                    .string(2, SwishAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<USPostalMoneyOrderAccountPayload> US_POSTAL_MONEY_ORDER_ACCOUNT_SCHEMA =
            CanonicalSchema.<USPostalMoneyOrderAccountPayload>newBuilder()
                    .string(1, USPostalMoneyOrderAccountPayload::getPostalAddress)
                    .string(2, USPostalMoneyOrderAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<UpholdAccountPayload> UPHOLD_ACCOUNT_SCHEMA =
            CanonicalSchema.<UpholdAccountPayload>newBuilder()
                    .string(1, UpholdAccountPayload::getAccountId)
                    .string(2, UpholdAccountPayload::getAccountOwner)
                    .build();
    private static final CanonicalSchema<CashAppAccountPayload> CASH_APP_ACCOUNT_SCHEMA =
            CanonicalSchema.<CashAppAccountPayload>newBuilder()
                    .string(1, CashAppAccountPayload::getCashTag)
                    .build();
    private static final CanonicalSchema<MoneyBeamAccountPayload> MONEY_BEAM_ACCOUNT_SCHEMA =
            CanonicalSchema.<MoneyBeamAccountPayload>newBuilder()
                    .string(1, MoneyBeamAccountPayload::getAccountId)
                    .build();
    private static final CanonicalSchema<VenmoAccountPayload> VENMO_ACCOUNT_SCHEMA =
            CanonicalSchema.<VenmoAccountPayload>newBuilder()
                    .string(1, VenmoAccountPayload::getVenmoUserName)
                    .string(2, VenmoAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<PopmoneyAccountPayload> POPMONEY_ACCOUNT_SCHEMA =
            CanonicalSchema.<PopmoneyAccountPayload>newBuilder()
                    .string(1, PopmoneyAccountPayload::getAccountId)
                    .string(2, PopmoneyAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<RevolutAccountPayload> REVOLUT_ACCOUNT_SCHEMA =
            CanonicalSchema.<RevolutAccountPayload>newBuilder()
                    .string(1, RevolutAccountPayload::getAccountId)
                    .string(2, RevolutAccountPayload::getUserName)
                    .build();
    private static final CanonicalSchema<WeChatPayAccountPayload> WE_CHAT_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<WeChatPayAccountPayload>newBuilder()
                    .string(1, WeChatPayAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<MoneyGramAccountPayload> MONEY_GRAM_ACCOUNT_SCHEMA =
            CanonicalSchema.<MoneyGramAccountPayload>newBuilder()
                    .string(1, MoneyGramAccountPayload::getHolderName)
                    .string(2, MoneyGramAccountPayload::getCountryCode)
                    .string(3, MoneyGramAccountPayload::getState)
                    .string(4, MoneyGramAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<HalCashAccountPayload> HAL_CASH_ACCOUNT_SCHEMA =
            CanonicalSchema.<HalCashAccountPayload>newBuilder()
                    .string(1, HalCashAccountPayload::getMobileNr)
                    .build();
    private static final CanonicalSchema<PromptPayAccountPayload> PROMPT_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<PromptPayAccountPayload>newBuilder()
                    .string(1, PromptPayAccountPayload::getPromptPayId)
                    .build();
    private static final CanonicalSchema<AdvancedCashAccountPayload> ADVANCED_CASH_ACCOUNT_SCHEMA =
            CanonicalSchema.<AdvancedCashAccountPayload>newBuilder()
                    .string(1, AdvancedCashAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<InstantCryptoCurrencyPayload> INSTANT_CRYPTO_CURRENCY_ACCOUNT_SCHEMA =
            CanonicalSchema.<InstantCryptoCurrencyPayload>newBuilder()
                    .string(1, InstantCryptoCurrencyPayload::getAddress)
                    .build();
    private static final CanonicalSchema<JapanBankAccountPayload> JAPAN_BANK_ACCOUNT_SCHEMA =
            CanonicalSchema.<JapanBankAccountPayload>newBuilder()
                    .string(1, JapanBankAccountPayload::getBankName)
                    .string(2, JapanBankAccountPayload::getBankCode)
                    .string(3, JapanBankAccountPayload::getBankBranchName)
                    .string(4, JapanBankAccountPayload::getBankBranchCode)
                    .string(5, JapanBankAccountPayload::getBankAccountType)
                    .string(6, JapanBankAccountPayload::getBankAccountName)
                    .string(7, JapanBankAccountPayload::getBankAccountNumber)
                    .build();
    private static final CanonicalSchema<TransferwiseAccountPayload> TRANSFERWISE_ACCOUNT_SCHEMA =
            CanonicalSchema.<TransferwiseAccountPayload>newBuilder()
                    .string(1, TransferwiseAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<AustraliaPayidAccountPayload> AUSTRALIA_PAYID_ACCOUNT_SCHEMA =
            CanonicalSchema.<AustraliaPayidAccountPayload>newBuilder()
                    .string(1, AustraliaPayidAccountPayload::getBankAccountName)
                    .string(2, AustraliaPayidAccountPayload::getPayid)
                    .build();
    private static final CanonicalSchema<AmazonGiftCardAccountPayload> AMAZON_GIFT_CARD_ACCOUNT_SCHEMA =
            CanonicalSchema.<AmazonGiftCardAccountPayload>newBuilder()
                    .string(1, AmazonGiftCardAccountPayload::getEmailOrMobileNr)
                    .string(2, AmazonGiftCardAccountPayload::getCountryCode)
                    .build();
    private static final CanonicalSchema<CashByMailAccountPayload> CASH_BY_MAIL_ACCOUNT_SCHEMA =
            CanonicalSchema.<CashByMailAccountPayload>newBuilder()
                    .string(1, CashByMailAccountPayload::getPostalAddress)
                    .string(2, CashByMailAccountPayload::getContact)
                    .string(3, CashByMailAccountPayload::getExtraInfo)
                    .build();
    private static final CanonicalSchema<CapitualAccountPayload> CAPITUAL_ACCOUNT_SCHEMA =
            CanonicalSchema.<CapitualAccountPayload>newBuilder()
                    .string(1, CapitualAccountPayload::getAccountNr)
                    .build();
    private static final CanonicalSchema<PayseraAccountPayload> PAYSERA_ACCOUNT_SCHEMA =
            CanonicalSchema.<PayseraAccountPayload>newBuilder()
                    .string(1, PayseraAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<PaxumAccountPayload> PAXUM_ACCOUNT_SCHEMA =
            CanonicalSchema.<PaxumAccountPayload>newBuilder()
                    .string(1, PaxumAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<SwiftAccountPayload> SWIFT_ACCOUNT_SCHEMA =
            CanonicalSchema.<SwiftAccountPayload>newBuilder()
                    .string(1, SwiftAccountPayload::getBeneficiaryName)
                    .string(2, SwiftAccountPayload::getBeneficiaryAccountNr)
                    .string(3, SwiftAccountPayload::getBeneficiaryAddress)
                    .string(4, SwiftAccountPayload::getBeneficiaryCity)
                    .string(5, SwiftAccountPayload::getBeneficiaryPhone)
                    .string(6, SwiftAccountPayload::getSpecialInstructions)
                    .string(7, SwiftAccountPayload::getBankSwiftCode)
                    .string(8, SwiftAccountPayload::getBankCountryCode)
                    .string(9, SwiftAccountPayload::getBankName)
                    .string(10, SwiftAccountPayload::getBankBranch)
                    .string(11, SwiftAccountPayload::getBankAddress)
                    .string(12, SwiftAccountPayload::getIntermediarySwiftCode)
                    .string(13, SwiftAccountPayload::getIntermediaryCountryCode)
                    .string(14, SwiftAccountPayload::getIntermediaryName)
                    .string(15, SwiftAccountPayload::getIntermediaryBranch)
                    .string(16, SwiftAccountPayload::getIntermediaryAddress)
                    .build();
    private static final CanonicalSchema<CelPayAccountPayload> CEL_PAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<CelPayAccountPayload>newBuilder()
                    .string(1, CelPayAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<MoneseAccountPayload> MONESE_ACCOUNT_SCHEMA =
            CanonicalSchema.<MoneseAccountPayload>newBuilder()
                    .string(1, MoneseAccountPayload::getMobileNr)
                    .string(2, MoneseAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<VerseAccountPayload> VERSE_ACCOUNT_SCHEMA =
            CanonicalSchema.<VerseAccountPayload>newBuilder()
                    .string(1, VerseAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<BsqSwapAccountPayload> BSQ_SWAP_ACCOUNT_SCHEMA =
            CanonicalSchema.<BsqSwapAccountPayload>newBuilder().build();
    private static final CanonicalSchema<SbpAccountPayload> SBP_ACCOUNT_SCHEMA =
            CanonicalSchema.<SbpAccountPayload>newBuilder()
                    .string(1, SbpAccountPayload::getHolderName)
                    .string(2, SbpAccountPayload::getMobileNumber)
                    .string(3, SbpAccountPayload::getBankName)
                    .build();

    private static final CanonicalSchema<NationalBankAccountPayload> NATIONAL_BANK_ACCOUNT_SCHEMA =
            CanonicalSchema.<NationalBankAccountPayload>newBuilder().build();
    private static final CanonicalSchema<SameBankAccountPayload> SAME_BANK_ACCOUNT_SCHEMA =
            CanonicalSchema.<SameBankAccountPayload>newBuilder().build();
    private static final CanonicalSchema<SpecificBanksAccountPayload> SPECIFIC_BANKS_ACCOUNT_SCHEMA =
            CanonicalSchema.<SpecificBanksAccountPayload>newBuilder()
                    .repeatedString(1, SpecificBanksAccountPayload::getAcceptedBanks)
                    .build();
    private static final CanonicalSchema<AchTransferAccountPayload> ACH_TRANSFER_ACCOUNT_SCHEMA =
            CanonicalSchema.<AchTransferAccountPayload>newBuilder()
                    .string(1, AchTransferAccountPayload::getHolderAddress)
                    .build();
    private static final CanonicalSchema<DomesticWireTransferAccountPayload> DOMESTIC_WIRE_TRANSFER_ACCOUNT_SCHEMA =
            CanonicalSchema.<DomesticWireTransferAccountPayload>newBuilder()
                    .string(1, DomesticWireTransferAccountPayload::getHolderAddress)
                    .build();

    private static final CanonicalSchema<BankAccountPayload> BANK_ACCOUNT_SCHEMA =
            CanonicalSchema.<BankAccountPayload>newBuilder()
                    .string(1, bankAccountPayload -> bankAccountPayload.holderName)
                    .string(2, bankAccountPayload -> bankAccountPayload.bankName)
                    .string(3, bankAccountPayload -> bankAccountPayload.bankId)
                    .string(4, bankAccountPayload -> bankAccountPayload.branchId)
                    .string(5, bankAccountPayload -> bankAccountPayload.accountNr)
                    .string(6, bankAccountPayload -> bankAccountPayload.accountType)
                    .string(7, bankAccountPayload -> bankAccountPayload.holderTaxId)
                    .oneof(9, bankAccountPayload -> as(bankAccountPayload, NationalBankAccountPayload.class), NATIONAL_BANK_ACCOUNT_SCHEMA)
                    .oneof(10, bankAccountPayload -> as(bankAccountPayload, SameBankAccountPayload.class), SAME_BANK_ACCOUNT_SCHEMA)
                    .oneof(11, bankAccountPayload -> as(bankAccountPayload, SpecificBanksAccountPayload.class), SPECIFIC_BANKS_ACCOUNT_SCHEMA)
                    .string(12, bankAccountPayload -> bankAccountPayload.nationalAccountId)
                    .oneof(13, bankAccountPayload -> as(bankAccountPayload, AchTransferAccountPayload.class), ACH_TRANSFER_ACCOUNT_SCHEMA)
                    .oneof(14, bankAccountPayload -> as(bankAccountPayload, DomesticWireTransferAccountPayload.class), DOMESTIC_WIRE_TRANSFER_ACCOUNT_SCHEMA)
                    .build();

    private static final CanonicalSchema<CashDepositAccountPayload> CASH_DEPOSIT_ACCOUNT_SCHEMA =
            CanonicalSchema.<CashDepositAccountPayload>newBuilder()
                    .string(1, cashDepositAccountPayload -> cashDepositAccountPayload.holderName)
                    .string(2, CashDepositAccountPayload::getHolderEmail)
                    .string(3, cashDepositAccountPayload -> cashDepositAccountPayload.bankName)
                    .string(4, cashDepositAccountPayload -> cashDepositAccountPayload.bankId)
                    .string(5, cashDepositAccountPayload -> cashDepositAccountPayload.branchId)
                    .string(6, cashDepositAccountPayload -> cashDepositAccountPayload.accountNr)
                    .string(7, cashDepositAccountPayload -> cashDepositAccountPayload.accountType)
                    .string(8, CashDepositAccountPayload::getRequirements)
                    .string(9, cashDepositAccountPayload -> cashDepositAccountPayload.holderTaxId)
                    .string(10, cashDepositAccountPayload -> cashDepositAccountPayload.nationalAccountId)
                    .build();
    private static final CanonicalSchema<SepaAccountPayload> SEPA_ACCOUNT_SCHEMA =
            CanonicalSchema.<SepaAccountPayload>newBuilder()
                    .string(1, SepaAccountPayload::getHolderName)
                    .string(2, SepaAccountPayload::getIban)
                    .string(3, SepaAccountPayload::getBic)
                    .string(4, SepaAccountPayload::getEmail)
                    .repeatedString(5, SepaAccountPayload::getAcceptedCountryCodes)
                    .build();
    private static final CanonicalSchema<WesternUnionAccountPayload> WESTERN_UNION_ACCOUNT_SCHEMA =
            CanonicalSchema.<WesternUnionAccountPayload>newBuilder()
                    .string(1, WesternUnionAccountPayload::getHolderName)
                    .string(2, WesternUnionAccountPayload::getCity)
                    .string(3, WesternUnionAccountPayload::getState)
                    .string(4, WesternUnionAccountPayload::getEmail)
                    .build();
    private static final CanonicalSchema<SepaInstantAccountPayload> SEPA_INSTANT_ACCOUNT_SCHEMA =
            CanonicalSchema.<SepaInstantAccountPayload>newBuilder()
                    .string(1, SepaInstantAccountPayload::getHolderName)
                    .string(2, SepaInstantAccountPayload::getIban)
                    .string(3, SepaInstantAccountPayload::getBic)
                    .repeatedString(4, SepaInstantAccountPayload::getAcceptedCountryCodes)
                    .build();
    private static final CanonicalSchema<F2FAccountPayload> F2F_ACCOUNT_SCHEMA =
            CanonicalSchema.<F2FAccountPayload>newBuilder()
                    .string(1, F2FAccountPayload::getContact)
                    .string(2, F2FAccountPayload::getCity)
                    .string(3, F2FAccountPayload::getExtraInfo)
                    .build();
    private static final CanonicalSchema<UpiAccountPayload> UPI_ACCOUNT_SCHEMA =
            CanonicalSchema.<UpiAccountPayload>newBuilder()
                    .string(1, UpiAccountPayload::getVirtualPaymentAddress)
                    .build();
    private static final CanonicalSchema<PaytmAccountPayload> PAYTM_ACCOUNT_SCHEMA =
            CanonicalSchema.<PaytmAccountPayload>newBuilder()
                    .string(1, PaytmAccountPayload::getEmailOrMobileNr)
                    .build();

    private static final CanonicalSchema<NeftAccountPayload> NEFT_ACCOUNT_SCHEMA =
            CanonicalSchema.<NeftAccountPayload>newBuilder().build();
    private static final CanonicalSchema<RtgsAccountPayload> RTGS_ACCOUNT_SCHEMA =
            CanonicalSchema.<RtgsAccountPayload>newBuilder().build();
    private static final CanonicalSchema<ImpsAccountPayload> IMPS_ACCOUNT_SCHEMA =
            CanonicalSchema.<ImpsAccountPayload>newBuilder().build();

    private static final CanonicalSchema<IfscBasedAccountPayload> IFSC_BASED_ACCOUNT_SCHEMA =
            CanonicalSchema.<IfscBasedAccountPayload>newBuilder()
                    .string(1, ifscBasedAccountPayload -> ifscBasedAccountPayload.holderName)
                    .string(2, ifscBasedAccountPayload -> ifscBasedAccountPayload.accountNr)
                    .string(3, ifscBasedAccountPayload -> ifscBasedAccountPayload.ifsc)
                    .oneof(4, ifscBasedAccountPayload -> as(ifscBasedAccountPayload, NeftAccountPayload.class), NEFT_ACCOUNT_SCHEMA)
                    .oneof(5, ifscBasedAccountPayload -> as(ifscBasedAccountPayload, RtgsAccountPayload.class), RTGS_ACCOUNT_SCHEMA)
                    .oneof(6, ifscBasedAccountPayload -> as(ifscBasedAccountPayload, ImpsAccountPayload.class), IMPS_ACCOUNT_SCHEMA)
                    .build();

    private static final CanonicalSchema<NequiAccountPayload> NEQUI_ACCOUNT_SCHEMA =
            CanonicalSchema.<NequiAccountPayload>newBuilder()
                    .string(1, NequiAccountPayload::getMobileNr)
                    .build();
    private static final CanonicalSchema<BizumAccountPayload> BIZUM_ACCOUNT_SCHEMA =
            CanonicalSchema.<BizumAccountPayload>newBuilder()
                    .string(1, BizumAccountPayload::getMobileNr)
                    .build();
    private static final CanonicalSchema<PixAccountPayload> PIX_ACCOUNT_SCHEMA =
            CanonicalSchema.<PixAccountPayload>newBuilder()
                    .string(1, PixAccountPayload::getPixKey)
                    .build();
    private static final CanonicalSchema<SatispayAccountPayload> SATISPAY_ACCOUNT_SCHEMA =
            CanonicalSchema.<SatispayAccountPayload>newBuilder()
                    .string(1, SatispayAccountPayload::getMobileNr)
                    .string(2, SatispayAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<StrikeAccountPayload> STRIKE_ACCOUNT_SCHEMA =
            CanonicalSchema.<StrikeAccountPayload>newBuilder()
                    .string(1, StrikeAccountPayload::getHolderName)
                    .build();
    private static final CanonicalSchema<TikkieAccountPayload> TIKKIE_ACCOUNT_SCHEMA =
            CanonicalSchema.<TikkieAccountPayload>newBuilder()
                    .string(1, TikkieAccountPayload::getIban)
                    .build();
    private static final CanonicalSchema<TransferwiseUsdAccountPayload> TRANSFERWISE_USD_ACCOUNT_SCHEMA =
            CanonicalSchema.<TransferwiseUsdAccountPayload>newBuilder()
                    .string(1, TransferwiseUsdAccountPayload::getEmail)
                    .string(2, TransferwiseUsdAccountPayload::getHolderName)
                    .string(3, TransferwiseUsdAccountPayload::getBeneficiaryAddress)
                    .build();
    private static final CanonicalSchema<MercadoPagoAccountPayload> MERCADO_PAGO_ACCOUNT_SCHEMA =
            CanonicalSchema.<MercadoPagoAccountPayload>newBuilder()
                    .string(1, MercadoPagoAccountPayload::getAccountHolderName)
                    .string(2, MercadoPagoAccountPayload::getAccountHolderId)
                    .build();

    private static final CanonicalSchema<CountryBasedPaymentAccountPayload> COUNTRY_BASED_PAYMENT_ACCOUNT_SCHEMA =
            CanonicalSchema.<CountryBasedPaymentAccountPayload>newBuilder()
                    .string(1, CountryBasedPaymentAccountPayload::getCountryCode)
                    .oneof(2, PaymentAccountPayloadCanonicalSchemas::asBankAccountPayload, BANK_ACCOUNT_SCHEMA)
                    .oneof(3, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, CashDepositAccountPayload.class), CASH_DEPOSIT_ACCOUNT_SCHEMA)
                    .oneof(4, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, SepaAccountPayload.class), SEPA_ACCOUNT_SCHEMA)
                    .oneof(5, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, WesternUnionAccountPayload.class), WESTERN_UNION_ACCOUNT_SCHEMA)
                    .oneof(6, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, SepaInstantAccountPayload.class), SEPA_INSTANT_ACCOUNT_SCHEMA)
                    .oneof(7, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, F2FAccountPayload.class), F2F_ACCOUNT_SCHEMA)
                    .oneof(9, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, UpiAccountPayload.class), UPI_ACCOUNT_SCHEMA)
                    .oneof(10, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, PaytmAccountPayload.class), PAYTM_ACCOUNT_SCHEMA)
                    .oneof(11, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, IfscBasedAccountPayload.class), IFSC_BASED_ACCOUNT_SCHEMA)
                    .oneof(12, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, NequiAccountPayload.class), NEQUI_ACCOUNT_SCHEMA)
                    .oneof(13, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, BizumAccountPayload.class), BIZUM_ACCOUNT_SCHEMA)
                    .oneof(14, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, PixAccountPayload.class), PIX_ACCOUNT_SCHEMA)
                    .oneof(15, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, SatispayAccountPayload.class), SATISPAY_ACCOUNT_SCHEMA)
                    .oneof(16, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, StrikeAccountPayload.class), STRIKE_ACCOUNT_SCHEMA)
                    .oneof(17, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, TikkieAccountPayload.class), TIKKIE_ACCOUNT_SCHEMA)
                    .oneof(18, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, TransferwiseUsdAccountPayload.class), TRANSFERWISE_USD_ACCOUNT_SCHEMA)
                    .oneof(19, countryBasedPaymentAccountPayload -> as(countryBasedPaymentAccountPayload, MercadoPagoAccountPayload.class), MERCADO_PAGO_ACCOUNT_SCHEMA)
                    .build();

    static final CanonicalSchema<PaymentAccountPayload> SCHEMA =
            CanonicalSchema.<PaymentAccountPayload>newBuilder()
                    .string(1, paymentAccountPayload -> paymentAccountPayload.id)
                    .string(2, paymentAccountPayload -> paymentAccountPayload.paymentMethodId)
                    .int64(3, paymentAccountPayload -> paymentAccountPayload.maxTradePeriod)
                    .oneof(4, paymentAccountPayload -> as(paymentAccountPayload, AliPayAccountPayload.class), ALI_PAY_ACCOUNT_SCHEMA)
                    .oneof(5, paymentAccountPayload -> as(paymentAccountPayload, ChaseQuickPayAccountPayload.class), CHASE_QUICK_PAY_ACCOUNT_SCHEMA)
                    .oneof(6, paymentAccountPayload -> as(paymentAccountPayload, ClearXchangeAccountPayload.class), CLEAR_XCHANGE_ACCOUNT_SCHEMA)
                    .oneof(7, paymentAccountPayload -> as(paymentAccountPayload, CountryBasedPaymentAccountPayload.class), COUNTRY_BASED_PAYMENT_ACCOUNT_SCHEMA)
                    .oneof(8, paymentAccountPayload -> as(paymentAccountPayload, CryptoCurrencyAccountPayload.class), CRYPTO_CURRENCY_ACCOUNT_SCHEMA)
                    .oneof(9, paymentAccountPayload -> as(paymentAccountPayload, FasterPaymentsAccountPayload.class), FASTER_PAYMENTS_ACCOUNT_SCHEMA)
                    .oneof(10, paymentAccountPayload -> as(paymentAccountPayload, InteracETransferAccountPayload.class), INTERAC_E_TRANSFER_ACCOUNT_SCHEMA)
                    .oneof(11, paymentAccountPayload -> as(paymentAccountPayload, OKPayAccountPayload.class), OK_PAY_ACCOUNT_SCHEMA)
                    .oneof(12, paymentAccountPayload -> as(paymentAccountPayload, PerfectMoneyAccountPayload.class), PERFECT_MONEY_ACCOUNT_SCHEMA)
                    .oneof(13, paymentAccountPayload -> as(paymentAccountPayload, SwishAccountPayload.class), SWISH_ACCOUNT_SCHEMA)
                    .oneof(14, paymentAccountPayload -> as(paymentAccountPayload, USPostalMoneyOrderAccountPayload.class), US_POSTAL_MONEY_ORDER_ACCOUNT_SCHEMA)
                    .mapStringToString(15,
                            paymentAccountPayload -> paymentAccountPayload.excludeFromJsonDataMap.getMap(),
                            SOURCE_ITERATION_ORDER)
                    .oneof(16, paymentAccountPayload -> as(paymentAccountPayload, UpholdAccountPayload.class), UPHOLD_ACCOUNT_SCHEMA)
                    .oneof(17, paymentAccountPayload -> as(paymentAccountPayload, CashAppAccountPayload.class), CASH_APP_ACCOUNT_SCHEMA)
                    .oneof(18, paymentAccountPayload -> as(paymentAccountPayload, MoneyBeamAccountPayload.class), MONEY_BEAM_ACCOUNT_SCHEMA)
                    .oneof(19, paymentAccountPayload -> as(paymentAccountPayload, VenmoAccountPayload.class), VENMO_ACCOUNT_SCHEMA)
                    .oneof(20, paymentAccountPayload -> as(paymentAccountPayload, PopmoneyAccountPayload.class), POPMONEY_ACCOUNT_SCHEMA)
                    .oneof(21, paymentAccountPayload -> as(paymentAccountPayload, RevolutAccountPayload.class), REVOLUT_ACCOUNT_SCHEMA)
                    .oneof(22, paymentAccountPayload -> as(paymentAccountPayload, WeChatPayAccountPayload.class), WE_CHAT_PAY_ACCOUNT_SCHEMA)
                    .oneof(23, paymentAccountPayload -> as(paymentAccountPayload, MoneyGramAccountPayload.class), MONEY_GRAM_ACCOUNT_SCHEMA)
                    .oneof(24, paymentAccountPayload -> as(paymentAccountPayload, HalCashAccountPayload.class), HAL_CASH_ACCOUNT_SCHEMA)
                    .oneof(25, paymentAccountPayload -> as(paymentAccountPayload, PromptPayAccountPayload.class), PROMPT_PAY_ACCOUNT_SCHEMA)
                    .oneof(26, paymentAccountPayload -> as(paymentAccountPayload, AdvancedCashAccountPayload.class), ADVANCED_CASH_ACCOUNT_SCHEMA)
                    .oneof(27, paymentAccountPayload -> as(paymentAccountPayload, InstantCryptoCurrencyPayload.class), INSTANT_CRYPTO_CURRENCY_ACCOUNT_SCHEMA)
                    .oneof(28, paymentAccountPayload -> as(paymentAccountPayload, JapanBankAccountPayload.class), JAPAN_BANK_ACCOUNT_SCHEMA)
                    .oneof(29, paymentAccountPayload -> as(paymentAccountPayload, TransferwiseAccountPayload.class), TRANSFERWISE_ACCOUNT_SCHEMA)
                    .oneof(30, paymentAccountPayload -> as(paymentAccountPayload, AustraliaPayidAccountPayload.class), AUSTRALIA_PAYID_ACCOUNT_SCHEMA)
                    .oneof(31, paymentAccountPayload -> as(paymentAccountPayload, AmazonGiftCardAccountPayload.class), AMAZON_GIFT_CARD_ACCOUNT_SCHEMA)
                    .oneof(32, paymentAccountPayload -> as(paymentAccountPayload, CashByMailAccountPayload.class), CASH_BY_MAIL_ACCOUNT_SCHEMA)
                    .oneof(33, paymentAccountPayload -> as(paymentAccountPayload, CapitualAccountPayload.class), CAPITUAL_ACCOUNT_SCHEMA)
                    .oneof(34, paymentAccountPayload -> as(paymentAccountPayload, PayseraAccountPayload.class), PAYSERA_ACCOUNT_SCHEMA)
                    .oneof(35, paymentAccountPayload -> as(paymentAccountPayload, PaxumAccountPayload.class), PAXUM_ACCOUNT_SCHEMA)
                    .oneof(36, paymentAccountPayload -> as(paymentAccountPayload, SwiftAccountPayload.class), SWIFT_ACCOUNT_SCHEMA)
                    .oneof(37, paymentAccountPayload -> as(paymentAccountPayload, CelPayAccountPayload.class), CEL_PAY_ACCOUNT_SCHEMA)
                    .oneof(38, paymentAccountPayload -> as(paymentAccountPayload, MoneseAccountPayload.class), MONESE_ACCOUNT_SCHEMA)
                    .oneof(39, paymentAccountPayload -> as(paymentAccountPayload, VerseAccountPayload.class), VERSE_ACCOUNT_SCHEMA)
                    .oneof(40, paymentAccountPayload -> as(paymentAccountPayload, BsqSwapAccountPayload.class), BSQ_SWAP_ACCOUNT_SCHEMA)
                    .oneof(41, paymentAccountPayload -> as(paymentAccountPayload, SbpAccountPayload.class), SBP_ACCOUNT_SCHEMA)
                    .build();

    private PaymentAccountPayloadCanonicalSchemas() {
    }

    static byte[] encode(PaymentAccountPayload paymentAccountPayload, CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(paymentAccountPayload, SCHEMA);
    }

    @Nullable
    private static BankAccountPayload asBankAccountPayload(CountryBasedPaymentAccountPayload paymentAccountPayload) {
        if (paymentAccountPayload instanceof BankAccountPayload bankAccountPayload &&
                !(paymentAccountPayload instanceof CashDepositAccountPayload)) {
            return bankAccountPayload;
        }
        return null;
    }

    @Nullable
    private static <T> T as(Object value, Class<T> type) {
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
