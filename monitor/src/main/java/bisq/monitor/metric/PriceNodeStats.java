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
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;

import bisq.asset.Asset;
import bisq.asset.AssetRegistry;

import bisq.network.p2p.NodeAddress;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Fetches fee and price data from the configured price nodes.
 * Based on the work of HarryMcFinned.
 *
 * @author Florian Reimair
 * @author HarryMcFinned
 *
 */
@Slf4j
public class PriceNodeStats extends Metric {

    private static final String HOSTS = "run.hosts";
    private static final String IGNORE = "dashTxFee ltcTxFee dogeTxFee";
    // poor mans JSON parser
    private final Pattern stringNumberPattern = Pattern.compile("\"(.+)\" ?: ?(\\d+)");
    private final Pattern pricePattern = Pattern.compile("\"price\" ?: ?([\\d.]+)");
    private final Pattern currencyCodePattern = Pattern.compile("\"currencyCode\" ?: ?\"([A-Z]+)\"");
    private final List<Object> assets = Arrays.asList(new AssetRegistry().stream().map(Asset::getTickerSymbol).toArray());

    public PriceNodeStats(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        try {
            // fetch proxy
            Tor tor = Tor.getDefault();
            checkNotNull(tor, "tor must not be null");
            Socks5Proxy proxy = tor.getProxy();

            String[] hosts = configuration.getProperty(HOSTS, "").split(",");

            Collections.shuffle(Arrays.asList(hosts));

            // for each configured host
            for (String current : hosts) {
                Map<String, String> result = new HashMap<>();
                // parse Url
                NodeAddress tmp = OnionParser.getNodeAddress(current);

                // connect
                try {
                    SocksSocket socket = new SocksSocket(proxy, tmp.getHostName(), tmp.getPort());

                    // prepare to receive data
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // ask for fee data
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                    out.println("GET /getFees/");
                    out.println();
                    out.flush();

                    // sift through the received lines and see if we got something json-like
                    String line;
                    while ((line = in.readLine()) != null) {
                        Matcher matcher = stringNumberPattern.matcher(line);
                        if (matcher.find())
                            if (!IGNORE.contains(matcher.group(1)))
                                result.put("fees." + matcher.group(1), matcher.group(2));
                    }

                    in.close();
                    out.close();
                    socket.close();

                    // connect
                    socket = new SocksSocket(proxy, tmp.getHostName(), tmp.getPort());

                    // prepare to receive data
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // ask for exchange rate data
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                    out.println("GET /getAllMarketPrices/");
                    out.println();
                    out.flush();

                    String currencyCode = "";
                    while ((line = in.readLine()) != null) {
                        Matcher currencyCodeMatcher = currencyCodePattern.matcher(line);
                        Matcher priceMatcher = pricePattern.matcher(line);
                        if (currencyCodeMatcher.find()) {
                            currencyCode = currencyCodeMatcher.group(1);
                            if (!assets.contains(currencyCode))
                                currencyCode = "";
                        } else if (!"".equals(currencyCode) && priceMatcher.find())
                            result.put("price." + currencyCode, priceMatcher.group(1));
                    }

                    // close all the things
                    in.close();
                    out.close();
                    socket.close();

                    // report
                    reporter.report(result, getName());

                    // only ask for data as long as we got none
                    if (!result.isEmpty())
                        break;
                } catch (IOException e) {
                    log.error("{} seems to be down. Trying next configured price node.", tmp.getHostName());
                    e.printStackTrace();
                }
            }
        } catch (TorCtlException | IOException e) {
            e.printStackTrace();
        }
    }
}
