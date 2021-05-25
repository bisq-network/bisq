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
            case PaymentMethod.UPHOLD_ID: //done
                return new UpholdAccount();
            case PaymentMethod.MONEY_BEAM_ID: //done
                return new MoneyBeamAccount();
            case PaymentMethod.POPMONEY_ID: //done
                return new PopmoneyAccount();
            case PaymentMethod.REVOLUT_ID: //done
                return new RevolutAccount();
            case PaymentMethod.PERFECT_MONEY_ID: //done
                return new PerfectMoneyAccount();
            case PaymentMethod.SEPA_ID: //done
                return new SepaAccount();
            case PaymentMethod.SEPA_INSTANT_ID: //done
                return new SepaInstantAccount();
            case PaymentMethod.FASTER_PAYMENTS_ID: //done
                return new FasterPaymentsAccount();
            case PaymentMethod.NATIONAL_BANK_ID: //done
                return new NationalBankAccount();
            case PaymentMethod.SAME_BANK_ID: //done
                return new SameBankAccount();
            case PaymentMethod.SPECIFIC_BANKS_ID: //done
                return new SpecificBanksAccount();
            case PaymentMethod.JAPAN_BANK_ID://done
                return new JapanBankAccount();
            case PaymentMethod.AUSTRALIA_PAYID_ID: //done
                return new AustraliaPayid();
            case PaymentMethod.ALI_PAY_ID: //done
                return new AliPayAccount();
            case PaymentMethod.WECHAT_PAY_ID: // done
                return new WeChatPayAccount();
            case PaymentMethod.SWISH_ID: //done
                return new SwishAccount();
            case PaymentMethod.CLEAR_X_CHANGE_ID:// done
                return new ClearXchangeAccount();
            case PaymentMethod.CHASE_QUICK_PAY_ID: // done
                return new ChaseQuickPayAccount();
            case PaymentMethod.INTERAC_E_TRANSFER_ID://done
                return new InteracETransferAccount();
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID: //done
                return new USPostalMoneyOrderAccount();
            case PaymentMethod.CASH_DEPOSIT_ID: //done
                return new CashDepositAccount();
            case PaymentMethod.BLOCK_CHAINS_ID://done
                return new CryptoCurrencyAccount();
            case PaymentMethod.MONEY_GRAM_ID: // done
                return new MoneyGramAccount();
            case PaymentMethod.WESTERN_UNION_ID:// done
                return new WesternUnionAccount();
            case PaymentMethod.HAL_CASH_ID: //done
                return new HalCashAccount();
            case PaymentMethod.F2F_ID: // done
                return new F2FAccount();
            case PaymentMethod.CASH_BY_MAIL_ID: //done
                return new CashByMailAccount();
            case PaymentMethod.PROMPT_PAY_ID: // done
                return new PromptPayAccount();
            case PaymentMethod.ADVANCED_CASH_ID: //done
                return new AdvancedCashAccount();
            case PaymentMethod.TRANSFERWISE_ID: // done
                return new TransferwiseAccount();
            case PaymentMethod.AMAZON_GIFT_CARD_ID: //done
                return new AmazonGiftCardAccount();
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID: //done
                return new InstantCryptoCurrencyAccount();

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
