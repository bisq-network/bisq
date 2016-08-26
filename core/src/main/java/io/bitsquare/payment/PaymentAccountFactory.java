/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentAccountFactory {
    private static final Logger log = LoggerFactory.getLogger(PaymentAccountFactory.class);

    public static PaymentAccount getPaymentAccount(PaymentMethod paymentMethod) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.OK_PAY_ID:
                return new OKPayAccount();
            case PaymentMethod.PERFECT_MONEY_ID:
                return new PerfectMoneyAccount();
            case PaymentMethod.SEPA_ID:
                return new SepaAccount();
            case PaymentMethod.NATIONAL_BANK_ID:
                return new NationalBankAccount();
            case PaymentMethod.SAME_BANK_ID:
                return new SameBankAccount();
            case PaymentMethod.SPECIFIC_BANKS_ID:
                return new SpecificBanksAccount();
            case PaymentMethod.ALI_PAY_ID:
                return new AliPayAccount();
            case PaymentMethod.SWISH_ID:
                return new SwishAccount();
            case PaymentMethod.CLEAR_X_CHANGE_ID:
                return new ClearXchangeAccount();
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
                return new USPostalMoneyOrderAccount();
            case PaymentMethod.CASH_DEPOSIT_ID:
                return new CashDepositAccount();
            case PaymentMethod.BLOCK_CHAINS_ID:
                return new CryptoCurrencyAccount();
            default:
                throw new RuntimeException("Not supported PaymentMethod: " + paymentMethod);
        }
    }
}
