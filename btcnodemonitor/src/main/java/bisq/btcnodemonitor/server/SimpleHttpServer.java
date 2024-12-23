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

package bisq.btcnodemonitor.server;

import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.nodes.LocalBitcoinNode;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.util.MathUtils;
import bisq.common.util.Profiler;
import bisq.common.util.SingleThreadExecutorUtils;

import org.bitcoinj.core.VersionMessage;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;



import bisq.btcnodemonitor.btc.PeerConncetionInfo;
import bisq.btcnodemonitor.btc.PeerConncetionModel;
import bisq.btcnodemonitor.btc.ServiceBits;
import spark.Spark;

@Slf4j
public class SimpleHttpServer {
    private final static String CLOSE_TAG = "</font><br/>";
    private final static String WARNING_ICON = "&#9888; ";
    private final static String ALERT_ICON = "&#9760; "; // &#9889;  &#9889;
    @Getter
    private final List<BtcNodes.BtcNode> providedBtcNodes;
    private final Map<String, BtcNodes.BtcNode> btcNodeByAddress;
    private final int port;
    private final PeerConncetionModel peerConncetionModel;
    private final String started;
    private final String networkInfo;
    private String html;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SimpleHttpServer(Config config, PeerConncetionModel peerConncetionModel) {
        this.peerConncetionModel = peerConncetionModel;
        started = new Date().toString();
        providedBtcNodes = peerConncetionModel.getProvidedBtcNodes();

        BaseCurrencyNetwork network = config.baseCurrencyNetwork;
        if (config.useTorForBtcMonitor) {
            port = network.isMainnet() ? 8000 : 8001;
            networkInfo = network.isMainnet() ? "TOR/MAIN_NET" : "TOR/REG_TEST";
            btcNodeByAddress = providedBtcNodes.stream()
                    .filter(e -> e.getOnionAddress() != null)
                    .collect(Collectors.toMap(BtcNodes.BtcNode::getOnionAddress, e -> e));
        } else {
            port = network.isMainnet() ? 8080 : 8081;
            networkInfo = network.isMainnet() ? "Clearnet/MAIN_NET" : "Clearnet/REG_TEST";

            if (new LocalBitcoinNode(config).shouldBeUsed()) {
                btcNodeByAddress = new HashMap<>();
                btcNodeByAddress.put("127.0.0.1", new BtcNodes.BtcNode("localhost", null, "127.0.0.1",
                        Config.baseCurrencyNetworkParameters().getPort(), "n/a"));
            } else {
                if (network.isMainnet()) {
                    btcNodeByAddress = providedBtcNodes.stream()
                            .filter(e -> e.getAddress() != null)
                            .collect(Collectors.toMap(BtcNodes.BtcNode::getAddress, e -> e));
                } else {
                    btcNodeByAddress = new HashMap<>();
                }
            }
        }
        html = "Monitor for Bitcoin nodes created for " + networkInfo;
    }

    public CompletableFuture<Void> start() {
        html = "Monitor for Bitcoin nodes starting up for " + networkInfo;
        return CompletableFuture.runAsync(() -> {
            log.info("Server listen on {}", port);
            Spark.port(port);
            Spark.get("/", (req, res) -> {
                log.info("Incoming request from: {}", req.userAgent());
                return html;
            });
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("SimpleHttpServer.start"));
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            log.info("stop Spark server");
            Spark.stop();
            log.info("Spark server stopped");
        });
    }

    public void onChange() {
        StringBuilder sb = new StringBuilder();
        sb.append("<result>" +
                        "<head>" +
                        "<style type=\"text/css\">" +
                        "   a {" +
                        "      text-decoration:none; color: black;" +
                        "   }" +
                        " #info { color: #333333; } " +
                        " #warn { color: #ff7700; } " +
                        " #error { color: #ff0000; } " +
                        "table, th, td {border: 1px solid black;}" +
                        "</style></head>" +
                        "<body><h3>")
                .append("Monitor for Bitcoin nodes using ").append(networkInfo)
                .append(", started at: ").append(started).append("<br/>")
                .append("System load: ").append(Profiler.getSystemLoad()).append("<br/>")
                .append("<br/>").append("<table style=\"width:100%\">")
                .append("<tr>")
                .append("<th align=\"left\">Node operator</th>")
                .append("<th align=\"left\">Connection attempts</th>")
                .append("<th align=\"left\">Node info</th>").append("</tr>");

        Map<String, PeerConncetionInfo> peersMap = peerConncetionModel.getMap();
        btcNodeByAddress.values().stream()
                .sorted(Comparator.comparing(BtcNodes.BtcNode::getId))
                .forEach(btcNode -> {
                    sb.append("<tr valign=\"top\">");
                    String address = btcNode.getAddress();
                    PeerConncetionInfo peerConncetionInfo = peersMap.get(address);
                    if (peersMap.containsKey(address)) {
                        sb.append("<td>").append(getOperatorInfo(btcNode, address)).append("</td>")
                                .append("<td>").append(getConnectionInfo(peerConncetionInfo)).append("</td>")
                                .append("<td>").append(getNodeInfo(peerConncetionInfo)).append("</td>");
                        sb.append("</tr>");
                        return;
                    }

                    address = btcNode.getOnionAddress();
                    peerConncetionInfo = peersMap.get(address);
                    if (peersMap.containsKey(address)) {
                        sb.append("<td>").append(getOperatorInfo(btcNode, address)).append("</td>")
                                .append("<td>").append(getConnectionInfo(peerConncetionInfo)).append("</td>")
                                .append("<td>").append(getNodeInfo(peerConncetionInfo)).append("</td>");
                    } else {
                       /* sb.append("<td>").append(getOperatorInfo(btcNode, null)).append("</td>")
                                .append("<td>").append("n/a").append("</td>");*/
                    }
                    sb.append("</tr>");
                });

        sb.append("</table></body></result>");
        html = sb.toString();
    }

    private String getOperatorInfo(BtcNodes.BtcNode btcNode, @Nullable String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(btcNode.getId()).append("<br/>");
        if (address != null) {
            sb.append("Address: ").append(address).append("<br/>");
        }
        return sb.toString();
    }

    private String getConnectionInfo(PeerConncetionInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("Num connection attempts: ").append(info.getNumConnectionAttempts()).append("<br/>");
        double failureRate = info.getFailureRate();
        String failureRateString = MathUtils.roundDouble(failureRate * 100, 2) + "%";
        if (failureRate >= 0.5) {
            failureRateString = asError(failureRateString, failureRateString);
        } else if (failureRate >= 0.25) {
            failureRateString = asWarn(failureRateString, failureRateString);
        } else if (failureRate > 0.) {
            failureRateString = asInfo(failureRateString, failureRateString);
        }
        sb.append("FailureRate (success/failures): ").append(failureRateString)
                .append("(").append(info.getNumSuccess()).append(" / ")
                .append(info.getNumFailures()).append(")").append("<br/>");

        info.getLastExceptionMessage().ifPresent(errorMessage -> {
            String allExceptionMessages = info.getAllExceptionMessages();
            int indexOfLastError = info.getLastAttemptWithException().map(info::getIndex).orElse(-1);
            String msg;
            String value = "Last error (connection attempt " + indexOfLastError + "): " + errorMessage;
            int tip = info.getConnectionAttempts().size() - 1;
            if (indexOfLastError >= tip - 1) {
                msg = asError(value, allExceptionMessages);
            } else if (indexOfLastError >= tip - 10) {
                msg = asWarn(value, allExceptionMessages);
            } else {
                msg = asInfo(value, allExceptionMessages);
                ;
            }
            sb.append(msg).append("<br/>");
        });
        sb.append("Duration to connect: ").append(MathUtils.roundDouble(info.getLastSuccessfulConnectTime() / 1000d, 2)).append(" sec").append("<br/>");
        return sb.toString();
    }

    private String getNodeInfo(PeerConncetionInfo info) {
        if (info.getLastSuccessfulConnected().isEmpty()) {
            return "";
        }
        PeerConncetionInfo.ConnectionAttempt attempt = info.getLastSuccessfulConnected().get();
        if (attempt.getVersionMessage().isEmpty()) {
            return "";
        }
        int index = info.getIndex(attempt) + 1;

        StringBuilder sb = new StringBuilder();
        VersionMessage versionMessage = attempt.getVersionMessage().get();
        long peerTime = versionMessage.time * 1000;
        long passed = System.currentTimeMillis() - attempt.getConnectionSuccessTs();
        String passedString = DurationFormatUtils.formatDurationWords(passed, true, true) + " ago";
        // String passedString = MathUtils.roundDouble(passed / 1000d, 2) + " sec. ago";
        if (passed > 300_000) {
            passedString = asWarn(passedString, passedString);
        }
        sb.append("Result from connection attempt ").append(index).append(":<br/>");
        sb.append("Connected ").append(passedString).append("<br/>");
        sb.append("Block height: ").append(versionMessage.bestHeight).append("<br/>");
        sb.append("Version: ").append(versionMessage.subVer.replace("/", "")).append(" (").append(versionMessage.clientVersion).append(")").append("<br/>");
        String serviceBits = ServiceBits.toString(versionMessage.localServices);
        sb.append("Services: ").append(serviceBits)
                .append(" (").append(versionMessage.localServices).append(")").append("<br/>");

        sb.append("Time: ").append(String.format(Locale.US, "%tF %tT", peerTime, peerTime));
        return sb.toString();
    }

    private static String decorate(String style, String value, String tooltip) {
        return "<b><a id=\"" + style + "\" href=\"#\" title=\"" + tooltip + "\">" + value + "</a></b>";
    }

    private static String asInfo(String value, String tooltip) {
        return decorate("info", value, tooltip);
    }

    private static String asWarn(String value, String tooltip) {
        return decorate("warn", value, tooltip);
    }

    private static String asError(String value, String tooltip) {
        return decorate("error", value, tooltip);
    }
}
