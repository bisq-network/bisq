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

package bisq.core.testutil;

import bisq.core.payment.PaymentAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.user.User;
import bisq.core.user.UserPayload;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.persistence.PersistenceManager;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserFixtures {
    // A User whose readPersisted reads the given payload and runs init(), as at startup
    public static User userReading(UserPayload persisted) {
        @SuppressWarnings("unchecked")
        PersistenceManager<UserPayload> persistenceManager = mock(PersistenceManager.class);
        doAnswer(invocation -> {
            invocation.<Consumer<UserPayload>>getArgument(1).accept(persisted);
            return null;
        }).when(persistenceManager).readPersisted(eq("UserPayload"), any(), any());
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getPubKeyRing()).thenReturn(mock(PubKeyRing.class));
        return new User(persistenceManager, keyRing);
    }

    public static PaymentAccount revolutAccount() {
        RevolutAccount account = new RevolutAccount();
        account.init();
        account.setAccountName("revolut");
        return account;
    }
}
