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

package bisq.core.user;

import bisq.core.locale.Res;
import bisq.core.payment.BsqSwapAccount;
import bisq.core.payment.PaymentAccount;

import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.testutil.UserFixtures.revolutAccount;
import static bisq.core.testutil.UserFixtures.userReading;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPaymentAccountsTest {
    private PaymentAccount persistedAccount;
    private User user;

    @BeforeEach
    void setUp() {
        Res.setup();
        persistedAccount = revolutAccount();
        UserPayload persisted = new UserPayload();
        // Mutable, as UserPayload.fromProto creates it
        persisted.setPaymentAccounts(new HashSet<>(Set.of(persistedAccount)));
        persisted.setCurrentPaymentAccount(persistedAccount);
        user = userReading(persisted);
    }

    // Services that are built while the persisted data hosts are collected, such as OfferFilterService
    // through TradeManager, observe the payment accounts before User.readPersisted runs init()
    @Test
    void aListenerFromBeforeInitGetsThePersistedAndTheLaterAccounts() {
        List<PaymentAccount> added = new ArrayList<>();
        user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) change -> {
            if (change.wasAdded())
                added.add(change.getElementAdded());
        });

        user.readPersisted(() -> {
        });
        PaymentAccount laterAccount = revolutAccount();
        user.addPaymentAccount(laterAccount);

        assertTrue(added.contains(persistedAccount));
        assertTrue(added.contains(laterAccount));
        assertEquals(user.getPaymentAccounts(), user.getPaymentAccountsAsObservable());
    }

    @Test
    void aListenerFromBeforeInitGetsTheCurrentAccount() {
        List<PaymentAccount> current = new ArrayList<>();
        user.currentPaymentAccountProperty().addListener((observable, oldValue, newValue) -> current.add(newValue));

        user.readPersisted(() -> {
        });
        PaymentAccount laterAccount = revolutAccount();
        user.addPaymentAccount(laterAccount);

        // init() also adds the BSQ swap account, which becomes the current account in between
        PaymentAccount bsqSwapAccount = user.getPaymentAccounts().stream()
                .filter(BsqSwapAccount.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(persistedAccount, bsqSwapAccount, laterAccount), current);
    }
}
