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

import bisq.core.offer.Offer;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

class PaymentAccounts {
    private static final Logger log = LoggerFactory.getLogger(PaymentAccounts.class);

    private final Set<PaymentAccount> accounts;
    private final AccountAgeWitnessService service;
    private final BiFunction<Offer, PaymentAccount, Boolean> validator;

    PaymentAccounts(Set<PaymentAccount> accounts, AccountAgeWitnessService service) {
        this(accounts, service, PaymentAccountUtil::isTakerPaymentAccountValidForOffer);
    }

    PaymentAccounts(Set<PaymentAccount> accounts, AccountAgeWitnessService service,
                    BiFunction<Offer, PaymentAccount, Boolean> validator) {
        this.accounts = accounts;
        this.service = service;
        this.validator = validator;
    }

    @Nullable
    PaymentAccount getOldestPaymentAccountForOffer(Offer offer) {
        List<PaymentAccount> sortedValidAccounts = sortValidAccounts(offer);

        logAccounts(sortedValidAccounts);

        return firstOrNull(sortedValidAccounts);
    }

    private List<PaymentAccount> sortValidAccounts(Offer offer) {
        Comparator<PaymentAccount> comparator = this::compareByAge;
        return accounts.stream()
                .filter(account -> validator.apply(offer, account))
                .sorted(comparator.reversed())
                .collect(Collectors.toList());
    }

    @Nullable
    private PaymentAccount firstOrNull(List<PaymentAccount> accounts) {
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    private void logAccounts(List<PaymentAccount> accounts) {
        if (log.isDebugEnabled()) {
            StringBuilder message = new StringBuilder("Valid accounts: \n");
            for (PaymentAccount account : accounts) {
                String accountName = account.getAccountName();
                String witnessHex = service.getMyWitnessHashAsHex(account.getPaymentAccountPayload());

                message.append("name = ")
                        .append(accountName)
                        .append("; witness hex = ")
                        .append(witnessHex)
                        .append(";\n");
            }

            log.debug(message.toString());
        }
    }

    private int compareByAge(PaymentAccount left, PaymentAccount right) {
        AccountAgeWitness leftWitness = service.getMyWitness(left.getPaymentAccountPayload());
        AccountAgeWitness rightWitness = service.getMyWitness(right.getPaymentAccountPayload());

        Date now = new Date();

        long leftAge = service.getAccountAge(leftWitness, now);
        long rightAge = service.getAccountAge(rightWitness, now);

        return Long.compare(leftAge, rightAge);
    }
}
