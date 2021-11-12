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

import bisq.core.payment.payload.PaymentMethod;

public class PaymentAccountFactory {
    public static PaymentAccount getPaymentAccount(PaymentMethod paymentMethod) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.UPHOLD_ID:
                return new UpholdAccount();
            case PaymentMethod.MONEY_BEAM_ID:
                return new MoneyBeamAccount();
            case PaymentMethod.POPMONEY_ID:
                return new PopmoneyAccount();
            case PaymentMethod.REVOLUT_ID:
                return new RevolutAccount();
            case PaymentMethod.PERFECT_MONEY_ID:
                return new PerfectMoneyAccount();
            case PaymentMethod.SEPA_ID:
                return new SepaAccount();
            case PaymentMethod.SEPA_INSTANT_ID:
                return new SepaInstantAccount();
            case PaymentMethod.FASTER_PAYMENTS_ID:
                return new FasterPaymentsAccount();
            case PaymentMethod.NATIONAL_BANK_ID:
                return new NationalBankAccount();
            case PaymentMethod.SAME_BANK_ID:
                return new SameBankAccount();
            case PaymentMethod.SPECIFIC_BANKS_ID:
                return new SpecificBanksAccount();
            case PaymentMethod.JAPAN_BANK_ID:
                return new JapanBankAccount();
            case PaymentMethod.AUSTRALIA_PAYID_ID:
                return new AustraliaPayid();
            case PaymentMethod.ALI_PAY_ID:
                return new AliPayAccount();
            case PaymentMethod.WECHAT_PAY_ID:
                return new WeChatPayAccount();
            case PaymentMethod.SWISH_ID:
                return new SwishAccount();
            case PaymentMethod.CLEAR_X_CHANGE_ID:
                return new ClearXchangeAccount();
            case PaymentMethod.CHASE_QUICK_PAY_ID:
                return new ChaseQuickPayAccount();
            case PaymentMethod.INTERAC_E_TRANSFER_ID:
                return new InteracETransferAccount();
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
                return new USPostalMoneyOrderAccount();
            case PaymentMethod.CASH_DEPOSIT_ID:
                return new CashDepositAccount();
            case PaymentMethod.BLOCK_CHAINS_ID:
                return new CryptoCurrencyAccount();
            case PaymentMethod.MONEY_GRAM_ID:
                return new MoneyGramAccount();
            case PaymentMethod.WESTERN_UNION_ID:
                return new WesternUnionAccount();
            case PaymentMethod.HAL_CASH_ID:
                return new HalCashAccount();
            case PaymentMethod.F2F_ID:
                return new F2FAccount();
            case PaymentMethod.CASH_BY_MAIL_ID:
                return new CashByMailAccount();
            case PaymentMethod.PROMPT_PAY_ID:
                return new PromptPayAccount();
            case PaymentMethod.ADVANCED_CASH_ID:
                return new AdvancedCashAccount();
            case PaymentMethod.TRANSFERWISE_ID:
                return new TransferwiseAccount();
            case PaymentMethod.TRANSFERWISE_USD_ID:
                return new TransferwiseUsdAccount();
            case PaymentMethod.PAYSERA_ID:
                return new PayseraAccount();
            case PaymentMethod.PAXUM_ID:
                return new PaxumAccount();
            case PaymentMethod.NEFT_ID:
                return new NeftAccount();
            case PaymentMethod.RTGS_ID:
                return new RtgsAccount();
            case PaymentMethod.IMPS_ID:
                return new ImpsAccount();
            case PaymentMethod.UPI_ID:
                return new UpiAccount();
            case PaymentMethod.PAYTM_ID:
                return new PaytmAccount();
            case PaymentMethod.NEQUI_ID:
                return new NequiAccount();
            case PaymentMethod.BIZUM_ID:
                return new BizumAccount();
            case PaymentMethod.PIX_ID:
                return new PixAccount();
            case PaymentMethod.AMAZON_GIFT_CARD_ID:
                return new AmazonGiftCardAccount();
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:
                return new InstantCryptoCurrencyAccount();
            case PaymentMethod.CAPITUAL_ID:
                return new CapitualAccount();
            case PaymentMethod.CELPAY_ID:
                return new CelPayAccount();
            case PaymentMethod.MONESE_ID:
                return new MoneseAccount();
            case PaymentMethod.SATISPAY_ID:
                return new SatispayAccount();
            case PaymentMethod.TIKKIE_ID:
                return new TikkieAccount();
            case PaymentMethod.VERSE_ID:
                return new VerseAccount();
            case PaymentMethod.STRIKE_ID:
                return new StrikeAccount();
            case PaymentMethod.SWIFT_ID:
                return new SwiftAccount();
            case PaymentMethod.ACH_TRANSFER_ID:
                return new AchTransferAccount();
            case PaymentMethod.DOMESTIC_WIRE_TRANSFER_ID:
                return new DomesticWireTransferAccount();
            case PaymentMethod.BSQ_SWAP_ID:
                return new BsqSwapAccount();

            // Cannot be deleted as it would break old trade history entries
            case PaymentMethod.OK_PAY_ID:
                return new OKPayAccount();
            case PaymentMethod.CASH_APP_ID:
                return new CashAppAccount();
            case PaymentMethod.VENMO_ID:
                return new VenmoAccount();

            default:
                throw new RuntimeException("Not supported PaymentMethod: " + paymentMethod);
        }
    }
}
