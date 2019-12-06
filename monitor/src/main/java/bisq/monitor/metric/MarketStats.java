/*
 * This file is part of Bisq.
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

package bisq.monitor.metric;

import bisq.monitor.Metric;
import bisq.monitor.Reporter;

import bisq.asset.Asset;
import bisq.asset.AssetRegistry;

import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import org.berndpruenster.netlayer.tor.TorCtlException;

import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Uses the markets API to retrieve market volume data.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public class MarketStats extends Metric {

    // poor mans JSON parser
    private final Pattern marketPattern = Pattern.compile("\"market\" ?: ?\"([a-z_]+)\"");
    private final Pattern amountPattern = Pattern.compile("\"amount\" ?: ?\"([\\d\\.]+)\"");
    private final Pattern volumePattern = Pattern.compile("\"volume\" ?: ?\"([\\d\\.]+)\"");
    private final Pattern timestampPattern = Pattern.compile("\"trade_date\" ?: ?([\\d]+)");

    private final String marketApi = "https://markets.bisq.network";
    private Long lastRun = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15));

    public MarketStats(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        try {
            // for each configured host
            Map<String, String> result = new HashMap<>();

            // assemble query
            Long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
            String query = "/api/trades?format=json&market=all&timestamp_from=" + lastRun + "&timestamp_to=" + now;
            lastRun = now; // thought about adding 1 second but what if a trade is done exactly in this one second?

            // connect
            URLConnection connection = new URL(marketApi + query).openConnection();

            // prepare to receive data
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String line, all = "";
            while ((line = in.readLine()) != null)
                all += ' ' + line;
            in.close();

            Arrays.stream(all.substring(0, all.length() - 2).split("}")).forEach(trade -> {
                Matcher market = marketPattern.matcher(trade);
                Matcher amount = amountPattern.matcher(trade);
                Matcher timestamp = timestampPattern.matcher(trade);
                market.find();
                if (market.group(1).endsWith("btc")) {
                    amount = volumePattern.matcher(trade);
                }
                amount.find();
                timestamp.find();
                reporter.report("volume." + market.group(1), amount.group(1), timestamp.group(1), getName());
            });
        } catch (IllegalStateException ignore) {
            // no match found
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
