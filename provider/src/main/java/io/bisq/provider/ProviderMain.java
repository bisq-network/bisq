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
import io.bisq.network.http.HttpException;
import io.bisq.provider.fee.FeeRequestService;
import io.bisq.provider.price.PriceRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

import static spark.Spark.get;
import static spark.Spark.port;

public class ProviderMain {
    private static final Logger log = LoggerFactory.getLogger(ProviderMain.class);

    public ProviderMain() {
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeyException, HttpException {
        Log.setup(System.getProperty("user.home") + File.separator + "provider");
        Log.setLevel(Level.INFO);

        port(8080);

        handleGetAllMarketPrices(args);
        handleGetFees();
    }

    private static void handleGetAllMarketPrices(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (args.length == 2) {
            String bitcoinAveragePrivKey = args[0];
            String bitcoinAveragePubKey = args[1];

            PriceRequestService priceRequestService = new PriceRequestService(bitcoinAveragePrivKey, bitcoinAveragePubKey);
            get("/getAllMarketPrices", (req, res) -> {
                log.info("Incoming getAllMarketPrices request from: " + req.userAgent());
                return priceRequestService.getJson();
            });
        } else {
            throw new IllegalArgumentException("You need to provide the BitcoinAverage API keys. Private key as first argument, public key as second argument.");
        }
    }

    private static void handleGetFees() throws IOException {
        FeeRequestService feeRequestService = new FeeRequestService();
        get("/getFees", (req, res) -> {
            log.info("Incoming getFees request from: " + req.userAgent());
            return feeRequestService.getJson();
        });
    }
}
