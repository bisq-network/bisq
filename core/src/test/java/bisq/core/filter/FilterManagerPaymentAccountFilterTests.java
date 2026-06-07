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

package bisq.core.filter;

import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.PerfectMoneyAccountPayload;
import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.BanFilter;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import java.io.File;

import java.nio.file.Path;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class FilterManagerPaymentAccountFilterTests {
    private static final String PAYMENT_METHOD_ID = PaymentMethod.PERFECT_MONEY_ID;
    private static final String GET_METHOD_NAME = "getAccountNr";

    private FilterManager filterManager;

    @BeforeEach
    void beforeEach(@TempDir Path tmpDir) {
        Config config = mock(Config.class);
        File configFile = tmpDir.resolve("configFile").toFile();
        doReturn(configFile).when(config).getConfigFile();

        filterManager = new FilterManager(
                mock(P2PService.class),
                mock(KeyRing.class),
                mock(User.class),
                mock(Preferences.class),
                DenyList.empty(),
                config,
                mock(PriceFeedNodeAddressProvider.class),
                mock(BanFilter.class),
                mock(PriceFeedService.class),
                false,
                true
        );
    }

    @Test
    void bannedPaymentAccountsUseHashedMatcher() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "account-123");
        filterManager.filterProperty().set(filter(List.of(new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, hash)),
                Collections.emptyList()));

        assertTrue(filterManager.arePeersPaymentAccountDataBanned(payload));
    }

    @Test
    void delayedPayoutPaymentAccountsUseHashedMatcher() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "account-123");
        filterManager.filterProperty().set(filter(Collections.emptyList(),
                List.of(new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, hash))));

        assertTrue(filterManager.isDelayedPayoutPaymentAccount(payload));
    }

    @Test
    void hashedMatcherDoesNotMatchWrongValue() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "other-account");
        filterManager.filterProperty().set(filter(List.of(new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, hash)),
                Collections.emptyList()));

        assertFalse(filterManager.arePeersPaymentAccountDataBanned(payload));
    }

    private PerfectMoneyAccountPayload perfectMoneyPayload(String accountNr) {
        PerfectMoneyAccountPayload payload = new PerfectMoneyAccountPayload(PAYMENT_METHOD_ID, "id");
        payload.setAccountNr(accountNr);
        return payload;
    }

    private Filter filter(List<PaymentAccountFilter> bannedPaymentAccounts,
                          List<PaymentAccountFilter> delayedPayoutPaymentAccounts) {
        return new Filter(
                Collections.emptyList(),
                Collections.emptyList(),
                bannedPaymentAccounts,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                Collections.emptyList(),
                false,
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                System.currentTimeMillis(),
                null,
                "",
                Collections.emptyList(),
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                false,
                false,
                1,
                Collections.emptyList(),
                1,
                1,
                1,
                1,
                delayedPayoutPaymentAccounts,
                Collections.emptyList(),
                Collections.emptyList(),
                UUID.randomUUID().toString(),
                false
        );
    }
}
