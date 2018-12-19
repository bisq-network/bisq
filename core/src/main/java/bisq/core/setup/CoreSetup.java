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

package bisq.core.setup;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;

import bisq.common.CommonOptionKeys;
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.util.Utilities;

import java.net.URISyntaxException;

import java.nio.file.Paths;

import ch.qos.logback.classic.Level;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreSetup {

    public static void setup(BisqEnvironment bisqEnvironment) {
        setupLog(bisqEnvironment);
        CoreNetworkCapabilities.setSupportedCapabilities(bisqEnvironment);
        Res.setup();
        CurrencyUtil.setup();
        bisqEnvironment.saveBaseCryptoNetwork(BisqEnvironment.getBaseCurrencyNetwork());

        Version.setBaseCryptoNetworkId(BisqEnvironment.getBaseCurrencyNetwork().ordinal());
        Version.printVersion();

        try {
            final String pathOfCodeSource = Utilities.getPathOfCodeSource();
            if (!pathOfCodeSource.endsWith("classes"))
                log.info("Path to Bisq jar file: " + pathOfCodeSource);
        } catch (URISyntaxException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    private static void setupLog(BisqEnvironment bisqEnvironment) {
        String logPath = Paths.get(bisqEnvironment.getAppDataDir(), "bisq").toString();
        Log.setup(logPath);
        log.info("\n\n\nLog files under: " + logPath);
        Utilities.printSysInfo();
        Log.setLevel(Level.toLevel(bisqEnvironment.getRequiredProperty(CommonOptionKeys.LOG_LEVEL_KEY)));
    }
}
