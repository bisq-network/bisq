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

package bisq.relay;

import bisq.common.app.Log;
import bisq.common.util.Utilities;

import org.apache.commons.codec.binary.Hex;

import java.io.File;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import static spark.Spark.get;
import static spark.Spark.port;

public class RelayMain {
    private static final Logger log = LoggerFactory.getLogger(RelayMain.class);
    private static final String VERSION = "0.1.0";
    private static RelayService relayService;

    static {
        // Need to set default locale initially otherwise we get problems at non-English OS
        Locale.setDefault(new Locale("en", Locale.getDefault().getCountry()));
    }

    /**
     * @param args      Pass port as program argument if other port than default port 8080 is wanted.
     */
    public static void main(String[] args) {
        final String logPath = System.getProperty("user.home") + File.separator + "provider";
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        log.info("Log files under: " + logPath);
        log.info("RelayVersion.VERSION: " + VERSION);
        Utilities.printSysInfo();


        String appleCertPwPath;
        if (args.length > 0)
            appleCertPwPath = args[0];
        else
            throw new RuntimeException("You need to set the path to the password text file for the Apple push certificate as first argument.");

        String appleCertPath;
        if (args.length > 1)
            appleCertPath = args[1];
        else
            throw new RuntimeException("You need to set the path to the Apple push certificate as second argument.");

        String appleBundleId;
        if (args.length > 2)
            appleBundleId = args[2];
        else
            throw new RuntimeException("You need to set the Apple bundle ID as third argument.");

        String androidCertPath;
        if (args.length > 3)
            androidCertPath = args[3];
        else
            throw new RuntimeException("You need to set the Android certificate path as 4th argument.");


        int port = 8080;
        if (args.length > 4)
            port = Integer.parseInt(args[4]);

        port(port);

        relayService = new RelayService(appleCertPwPath, appleCertPath, appleBundleId, androidCertPath);

        handleRelay();

        keepRunning();
    }

    private static void handleRelay() {
        get("/relay", (request, response) -> {
            log.info("Incoming relay request from: " + request.userAgent());
            boolean isAndroid = request.queryParams("isAndroid").equalsIgnoreCase("true");
            boolean useSound = request.queryParams("snd").equalsIgnoreCase("true");
            String token = new String(Hex.decodeHex(request.queryParams("token").toCharArray()), "UTF-8");
            String encryptedMessage = new String(Hex.decodeHex(request.queryParams("msg").toCharArray()), "UTF-8");
            log.info("isAndroid={}\nuseSound={}\napsTokenHex={}\nencryptedMessage={}", isAndroid, useSound, token,
                encryptedMessage);
            if (isAndroid) {
                return relayService.sendAndroidMessage(token, encryptedMessage, useSound);
            } else {
                boolean isProduction = request.queryParams("isProduction").equalsIgnoreCase("true");
                boolean isContentAvailable = request.queryParams("isContentAvailable").equalsIgnoreCase("true");
                return relayService.sendAppleMessage(isProduction, isContentAvailable, token, encryptedMessage, useSound);
            }
        });
    }

    private static void keepRunning() {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
