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

package io.bisq.provider;

import ch.qos.logback.classic.Level;
import io.bisq.common.app.Log;
import io.bisq.common.app.Version;
import io.bisq.common.util.Utilities;
import io.bisq.network.http.HttpException;
import io.bisq.provider.fee.FeeRequestService;
import io.bisq.provider.fee.providers.BtcFeesProvider;
import io.bisq.provider.price.PriceRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static spark.Spark.get;
import static spark.Spark.port;

public class ProviderMain {
    private static final Logger log = LoggerFactory.getLogger(ProviderMain.class);

    static {
        // Need to set default locale initially otherwise we get problems at non-english OS
        Locale.setDefault(new Locale("en", Locale.getDefault().getCountry()));

        Utilities.removeCryptographyRestrictions();
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeyException, HttpException {
        final String logPath = System.getProperty("user.home") + File.separator + "provider";
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        log.info("Log files under: " + logPath);
        log.info("ProviderVersion.VERSION: " + ProviderVersion.VERSION);
        log.info("Bisq exchange Version{" +
                "VERSION=" + Version.VERSION +
                ", P2P_NETWORK_VERSION=" + Version.P2P_NETWORK_VERSION +
                ", LOCAL_DB_VERSION=" + Version.LOCAL_DB_VERSION +
                ", TRADE_PROTOCOL_VERSION=" + Version.TRADE_PROTOCOL_VERSION +
                ", BASE_CURRENCY_NETWORK=NOT SET" +
                ", getP2PNetworkId()=NOT SET" +
                '}');
        Utilities.printSysInfo();

        String bitcoinAveragePrivKey = null;
        String bitcoinAveragePubKey = null;
        int capacity = BtcFeesProvider.CAPACITY;
        int maxBlocks = BtcFeesProvider.MAX_BLOCKS;
        long requestIntervalInMs = TimeUnit.MINUTES.toMillis(FeeRequestService.REQUEST_INTERVAL_MIN);

        // extract command line arguments
        if (args.length < 2) {
            log.error("You need to provide the BitcoinAverage API keys. Private key as first argument, public key as second argument.");
            System.exit(1);
        }
        if (args.length >= 2) {
            bitcoinAveragePrivKey = args[0];
            bitcoinAveragePubKey = args[1];
        }
        if (args.length >= 4) {
            capacity = Integer.valueOf(args[2]);
            maxBlocks = Integer.valueOf(args[3]);
        }
        if (args.length >= 5) {
            requestIntervalInMs = TimeUnit.MINUTES.toMillis(Long.valueOf(args[4]));
        }

        port(8080);

        handleGetAllMarketPrices(bitcoinAveragePrivKey, bitcoinAveragePubKey);
        handleGetFees(capacity, maxBlocks, requestIntervalInMs);
        handleGetVersion();
        handleGetParams(capacity, maxBlocks, requestIntervalInMs);
    }

    private static void handleGetAllMarketPrices(String bitcoinAveragePrivKey, String bitcoinAveragePubKey)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        PriceRequestService priceRequestService = new PriceRequestService(bitcoinAveragePrivKey, bitcoinAveragePubKey);
        get("/getAllMarketPrices", (req, res) -> {
            log.info("Incoming getAllMarketPrices request from: " + req.userAgent());
            return priceRequestService.getJson();
        });
    }

    private static void handleGetFees(int capacity, int maxBlocks, long requestIntervalInMs) throws IOException {
        FeeRequestService feeRequestService = new FeeRequestService(capacity, maxBlocks, requestIntervalInMs);
        get("/getFees", (req, res) -> {
            log.info("Incoming getFees request from: " + req.userAgent());
            return feeRequestService.getJson();
        });
    }

    private static void handleGetVersion() throws IOException {
        get("/getVersion", (req, res) -> {
            log.info("Incoming getVersion request from: " + req.userAgent());
            return ProviderVersion.VERSION;
        });
    }

    private static void handleGetParams(int capacity, int maxBlocks, long requestIntervalInMs) throws IOException {
        get("/getParams", (req, res) -> {
            log.info("Incoming getParams request from: " + req.userAgent());
            return capacity + ";" + maxBlocks + ";" + requestIntervalInMs;
        });
    }
}
