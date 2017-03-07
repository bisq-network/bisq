/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.pricefeed;

import ch.qos.logback.classic.Level;
import io.bitsquare.app.Log;
import io.bitsquare.http.HttpException;
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

public class PriceFeedMain {
    private static final Logger log = LoggerFactory.getLogger(PriceFeedMain.class);

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeyException, HttpException {
        Log.setup(System.getProperty("user.home") + File.separator + "priceFeedProvider");
        Log.setLevel(Level.INFO);
        if (args.length == 2) {
            String bitcoinAveragePrivKey = args[0];
            String bitcoinAveragePubKey = args[1];

            PriceRequestService priceRequestService = new PriceRequestService(bitcoinAveragePrivKey, bitcoinAveragePubKey);
            port(8080);
            get("/all", (req, res) -> {
                log.info("Incoming request from: " + req.userAgent());
                return priceRequestService.getJson();
            });
        } else {
            throw new IllegalArgumentException("You need to provide the BitcoinAverage API keys. Private key as first argument, public key as second argument.");
        }
    }
}
