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

package bisq.core.api;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.PerfectMoneyAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.User;

import bisq.common.config.Config;

import javax.inject.Inject;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.payment.payload.PaymentMethod.*;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
class CorePaymentAccountsService {

    private final Config config;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final User user;

    @Inject
    public CorePaymentAccountsService(Config config,
                                      AccountAgeWitnessService accountAgeWitnessService,
                                      User user) {
        this.config = config;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.user = user;
    }

    void createPaymentAccount(String paymentMethodId,
                              String accountName,
                              String accountNumber,
                              String currencyCode) {

        PaymentAccount paymentAccount = getNewPaymentAccount(paymentMethodId,
                accountName,
                accountNumber,
                currencyCode);

        user.addPaymentAccountIfNotExists(paymentAccount);

        // Don't do this on mainnet until thoroughly tested.
        if (config.baseCurrencyNetwork.isRegtest())
            accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());

        log.info("Payment account {} saved", paymentAccount.getId());
    }

    Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    private PaymentAccount getNewPaymentAccount(String paymentMethodId,
                                                String accountName,
                                                String accountNumber,
                                                String currencyCode) {
        PaymentAccount paymentAccount = null;
        PaymentMethod paymentMethod = getPaymentMethodById(paymentMethodId);

        switch (paymentMethod.getId()) {
            case UPHOLD_ID:
            case MONEY_BEAM_ID:
            case POPMONEY_ID:
            case REVOLUT_ID:
                //noinspection DuplicateBranchesInSwitch
                log.error("PaymentMethod {} not supported yet.", paymentMethod);
                break;
            case PERFECT_MONEY_ID:
                // Create and persist a PerfectMoney dummy payment account.  There is no
                // guard against creating accounts with duplicate names & numbers, only
                // the uuid and creation date are unique.
                paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
                paymentAccount.init();
                paymentAccount.setAccountName(accountName);
                ((PerfectMoneyAccount) paymentAccount).setAccountNr(accountNumber);
                paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
                break;
            case SEPA_ID:
            case SEPA_INSTANT_ID:
            case FASTER_PAYMENTS_ID:
            case NATIONAL_BANK_ID:
            case SAME_BANK_ID:
            case SPECIFIC_BANKS_ID:
            case JAPAN_BANK_ID:
            case ALI_PAY_ID:
            case WECHAT_PAY_ID:
            case SWISH_ID:
            case CLEAR_X_CHANGE_ID:
            case CHASE_QUICK_PAY_ID:
            case INTERAC_E_TRANSFER_ID:
            case US_POSTAL_MONEY_ORDER_ID:
            case MONEY_GRAM_ID:
            case WESTERN_UNION_ID:
            case CASH_DEPOSIT_ID:
            case HAL_CASH_ID:
            case F2F_ID:
            case PROMPT_PAY_ID:
            case ADVANCED_CASH_ID:
            default:
                log.error("PaymentMethod {} not supported yet.", paymentMethod);
                break;
        }

        checkNotNull(paymentAccount,
                "Could not create payment account with paymentMethodId "
                        + paymentMethodId + ".");
        return paymentAccount;
    }
}
