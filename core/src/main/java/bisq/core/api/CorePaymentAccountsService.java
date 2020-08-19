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

    public void createPaymentAccount(String accountName, String accountNumber, String fiatCurrencyCode) {
        // Create and persist a PerfectMoney dummy payment account.  There is no guard
        // against creating accounts with duplicate names & numbers, only the uuid and
        // creation date are unique.
        PaymentMethod dummyPaymentMethod = PaymentMethod.getDummyPaymentMethod(PaymentMethod.PERFECT_MONEY_ID);
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(dummyPaymentMethod);
        paymentAccount.init();
        paymentAccount.setAccountName(accountName);
        ((PerfectMoneyAccount) paymentAccount).setAccountNr(accountNumber);
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(fiatCurrencyCode.toUpperCase()));
        user.addPaymentAccount(paymentAccount);

        // Don't do this on mainnet until thoroughly tested.
        if (config.baseCurrencyNetwork.isRegtest())
            accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());

        log.info("Payment account {} saved", paymentAccount.getId());
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }
}
