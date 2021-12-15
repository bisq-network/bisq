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

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.Offer;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

class PaymentAccounts {
    private static final Logger log = LoggerFactory.getLogger(PaymentAccounts.class);

    private final Set<PaymentAccount> accounts;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BiPredicate<Offer, PaymentAccount> validator;

    PaymentAccounts(Set<PaymentAccount> accounts, AccountAgeWitnessService accountAgeWitnessService) {
        this(accounts, accountAgeWitnessService, PaymentAccountUtil::isPaymentAccountValidForOffer);
    }

    PaymentAccounts(Set<PaymentAccount> accounts, AccountAgeWitnessService accountAgeWitnessService,
                    BiPredicate<Offer, PaymentAccount> validator) {
        this.accounts = accounts;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.validator = validator;
    }

    @Nullable
    PaymentAccount getOldestPaymentAccountForOffer(Offer offer) {
        List<PaymentAccount> sortedValidAccounts = sortValidAccounts(offer);

        logAccounts(sortedValidAccounts);

        return firstOrNull(sortedValidAccounts);
    }

    private List<PaymentAccount> sortValidAccounts(Offer offer) {
        Comparator<PaymentAccount> comparator = this::compareByTradeLimit;
        return accounts.stream()
                .filter(account -> validator.test(offer, account))
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
                String witnessHex = accountAgeWitnessService.getMyWitnessHashAsHex(account.getPaymentAccountPayload());

                message.append("name = ")
                        .append(accountName)
                        .append("; witness hex = ")
                        .append(witnessHex)
                        .append(";\n");
            }

            log.debug(message.toString());
        }
    }

    // Accounts ranked by trade limit
    private int compareByTradeLimit(PaymentAccount left, PaymentAccount right) {
        // Mature accounts count as infinite sign age
        if (accountAgeWitnessService.myHasTradeLimitException(left)) {
            return !accountAgeWitnessService.myHasTradeLimitException(right) ? 1 : 0;
        }
        if (accountAgeWitnessService.myHasTradeLimitException(right)) {
            return -1;
        }

        AccountAgeWitness leftWitness = accountAgeWitnessService.getMyWitness(left.getPaymentAccountPayload());
        AccountAgeWitness rightWitness = accountAgeWitnessService.getMyWitness(right.getPaymentAccountPayload());

        Date now = new Date();

        long leftSignAge = accountAgeWitnessService.getWitnessSignAge(leftWitness, now);
        long rightSignAge = accountAgeWitnessService.getWitnessSignAge(rightWitness, now);

        return Long.compare(leftSignAge, rightSignAge);
    }
}
