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

package io.bisq.core.app;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.network.crypto.EncryptionService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public abstract class AppSetup {
    protected final EncryptionService encryptionService;
    protected final KeyRing keyRing;

    @Inject
    public AppSetup(EncryptionService encryptionService,
                    KeyRing keyRing) {
        // we need to reference it so the seed node stores tradeStatistics
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;

        Version.setBaseCryptoNetworkId(BisqEnvironment.getBaseCurrencyNetwork().ordinal());
        Version.printVersion();
    }

    public void start() {
        SetupUtils.checkCryptoSetup(keyRing, encryptionService, () -> {
            initPersistedDataHosts();
            initBasicServices();
        }, throwable -> {
            log.error(throwable.getMessage());
            throwable.printStackTrace();
            System.exit(1);
        });
    }

    abstract void initPersistedDataHosts();

    abstract void initBasicServices();
}
