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
            case PaymentMethod.PROMPT_PAY_ID:
                return new PromptPayAccount();
            case PaymentMethod.ADVANCED_CASH_ID:
                return new AdvancedCashAccount();
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:
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
