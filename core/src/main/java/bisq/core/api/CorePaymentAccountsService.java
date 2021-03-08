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
import bisq.core.api.model.PaymentAccountForm;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.User;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Singleton
@Slf4j
class CorePaymentAccountsService {

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final PaymentAccountForm paymentAccountForm;
    private final User user;

    @Inject
    public CorePaymentAccountsService(AccountAgeWitnessService accountAgeWitnessService,
                                      PaymentAccountForm paymentAccountForm,
                                      User user) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.paymentAccountForm = paymentAccountForm;
        this.user = user;
    }

    PaymentAccount createPaymentAccount(String jsonString) {
        PaymentAccount paymentAccount = paymentAccountForm.toPaymentAccount(jsonString);
        verifyPaymentAccountHasRequiredFields(paymentAccount);
        user.addPaymentAccountIfNotExists(paymentAccount);
        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        log.info("Saved payment account with id {} and payment method {}.",
                paymentAccount.getId(),
                paymentAccount.getPaymentAccountPayload().getPaymentMethodId());
        return paymentAccount;
    }

    Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    List<PaymentMethod> getFiatPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(paymentMethod -> !paymentMethod.isAsset())
                .sorted(Comparator.comparing(PaymentMethod::getId))
                .collect(Collectors.toList());
    }

    String getPaymentAccountFormAsString(String paymentMethodId) {
        File jsonForm = getPaymentAccountForm(paymentMethodId);
        jsonForm.deleteOnExit(); // If just asking for a string, delete the form file.
        return paymentAccountForm.toJsonString(jsonForm);
    }

    File getPaymentAccountForm(String paymentMethodId) {
        return paymentAccountForm.getPaymentAccountForm(paymentMethodId);
    }

    private void verifyPaymentAccountHasRequiredFields(PaymentAccount paymentAccount) {
        // Do checks here to make sure required fields are populated.
        if (paymentAccount.isTransferwiseAccount() && paymentAccount.getTradeCurrencies().isEmpty())
            throw new IllegalArgumentException(format("no trade currencies defined for %s payment account",
                    paymentAccount.getPaymentMethod().getDisplayString().toLowerCase()));
    }
}
