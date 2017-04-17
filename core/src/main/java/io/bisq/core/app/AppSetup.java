/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.app;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.core.user.Preferences;
import io.bisq.network.crypto.EncryptionService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class AppSetup {
    protected final EncryptionService encryptionService;
    protected final KeyRing keyRing;

    @Inject
    public AppSetup(BisqEnvironment bisqEnvironment,
                    EncryptionService encryptionService,
                    KeyRing keyRing,
                    Preferences preferences) {
        // we need to reference it so the seed node stores tradeStatistics
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;

        preferences.init();

        Version.setBtcNetworkId(bisqEnvironment.getBitcoinNetwork().ordinal());
        Version.printVersion();
    }

    public void start() {
        SetupUtils.checkCryptoSetup(keyRing, encryptionService, this::startBasicServices, throwable -> {
            log.error(throwable.getMessage());
            throwable.printStackTrace();
            System.exit(1);
        });
    }

    protected void startBasicServices() {
    }
}
