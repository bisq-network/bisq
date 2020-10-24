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

package bisq.core.app;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.UserThread;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetupUtils {

    public static BooleanProperty readFromResources(P2PDataStorage p2PDataStorage, Config config) {
        BooleanProperty result = new SimpleBooleanProperty();
        new Thread(() -> {
            // Used to load different files per base currency (EntryMap_BTC_MAINNET, EntryMap_LTC,...)
            final BaseCurrencyNetwork baseCurrencyNetwork = config.baseCurrencyNetwork;
            final String postFix = "_" + baseCurrencyNetwork.name();
            long ts = new Date().getTime();
            p2PDataStorage.readFromResources(postFix);
            log.info("readFromResources took {} ms", (new Date().getTime() - ts));
            UserThread.execute(() -> result.set(true));
        }, "BisqSetup-readFromResources").start();
        return result;
    }
}
