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

package bisq.monitor.metrics;

import bisq.monitor.MonitorOptionKeys;

import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.locale.Res;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;

import org.bitcoinj.core.Peer;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricsModel {
    private final DateFormat dateFormat = new SimpleDateFormat("MMMMM dd, HH:mm:ss");
    @Getter
    private String resultAsString;
    @Getter
    private String resultAsHtml;
    private SeedNodeRepository seedNodeRepository;
    private SlackApi slackSeedApi, slackBtcApi, slackProviderApi;
    private BtcNodes btcNodes;
    @Setter
    private long lastCheckTs;
    private long btcNodeUptimeTs;
    private int totalErrors = 0;
    private HashMap<NodeAddress, Metrics> map = new HashMap<>();
    private List<Peer> connectedPeers;
    private Map<Tuple2<BtcNodes.BtcNode, Boolean>, Integer> btcNodeDownTimeMap = new HashMap<>();
    private Map<Tuple2<BtcNodes.BtcNode, Boolean>, Integer> btcNodeUpTimeMap = new HashMap<>();
    @Getter
    private Set<NodeAddress> nodesInError = new HashSet<>();

    @Inject
    public MetricsModel(SeedNodeRepository seedNodeRepository,
                        BtcNodes btcNodes,
                        WalletsSetup walletsSetup,
                        @Named(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL) String slackUrlSeedChannel,
                        @Named(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL) String slackUrlBtcChannel,
                        @Named(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL) String slackUrlProviderChannel) {
        this.seedNodeRepository = seedNodeRepository;
        this.btcNodes = btcNodes;
        if (!slackUrlSeedChannel.isEmpty())
            slackSeedApi = new SlackApi(slackUrlSeedChannel);
        if (!slackUrlBtcChannel.isEmpty())
            slackBtcApi = new SlackApi(slackUrlBtcChannel);
        if (!slackUrlProviderChannel.isEmpty())
            slackProviderApi = new SlackApi(slackUrlProviderChannel);

        walletsSetup.connectedPeersProperty().addListener((observable, oldValue, newValue) -> {
            connectedPeers = newValue;
        });
    }

    public void addToMap(NodeAddress nodeAddress, Metrics metrics) {
        map.put(nodeAddress, metrics);
    }

    public Metrics getMetrics(NodeAddress nodeAddress) {
        return map.get(nodeAddress);
    }

    public void updateReport() {
        if (btcNodeUptimeTs == 0)
            btcNodeUptimeTs = new Date().getTime();

        Map<String, Double> accumulatedValues = new HashMap<>();
        final double[] items = {0};
        List<Map.Entry<NodeAddress, Metrics>> entryList = map.entrySet().stream()
                .sorted(Comparator.comparing(entrySet -> seedNodeRepository.getOperator(entrySet.getKey())))
                .collect(Collectors.toList());

        totalErrors = 0;
        entryList.stream().forEach(e -> {
            totalErrors += e.getValue().errorMessages.stream().filter(s -> !s.isEmpty()).count();
            final List<Map<String, Integer>> receivedObjectsList = e.getValue().getReceivedObjectsList();
            if (!receivedObjectsList.isEmpty()) {
                items[0] += 1;
                Map<String, Integer> last = receivedObjectsList.get(receivedObjectsList.size() - 1);
                last.entrySet().stream().forEach(e2 -> {
                    int accuValue = e2.getValue();
                    if (accumulatedValues.containsKey(e2.getKey()))
                        accuValue += accumulatedValues.get(e2.getKey());

                    accumulatedValues.put(e2.getKey(), (double) accuValue);
                });
            }
        });

        Map<String, Double> averageValues = new HashMap<>();
        accumulatedValues.entrySet().stream().forEach(e -> {
            averageValues.put(e.getKey(), e.getValue() / items[0]);
        });

        Calendar calendar = new GregorianCalendar();
        calendar.setTimeZone(TimeZone.getTimeZone("CET"));
        calendar.setTimeInMillis(lastCheckTs);
        final String time = calendar.getTime().toString();

        StringBuilder html = new StringBuilder();
        html.append("<html>" +
                "<head>" +
                "<style>table, th, td {border: 1px solid black;}</style>" +
                "</head>" +
                "<body>" +
                "<h3>")
                .append("Seed nodes in error: <b>" + totalErrors + "</b><br/>" +
                        "Last check started at: " + time + "<br/></h3>" +
                        "<table style=\"width:100%\">" +
                        "<tr>" +
                        "<th align=\"left\">Operator</th>" +
                        "<th align=\"left\">Node address</th>" +
                        "<th align=\"left\">Total num requests</th>" +
                        "<th align=\"left\">Total num errors</th>" +
                        "<th align=\"left\">Last request</th>" +
                        "<th align=\"left\">Last response</th>" +
                        "<th align=\"left\">RRT average</th>" +
                        "<th align=\"left\">Num requests (retries)</th>" +
                        "<th align=\"left\">Last error message</th>" +
                        "<th align=\"left\">Last data</th>" +
                        "<th align=\"left\">Data deviation last request</th>" +
                        "</tr>");

        StringBuilder sb = new StringBuilder();
        sb.append("Seed nodes in error:" + totalErrors);
        sb.append("\nLast check started at: " + time + "\n");

        entryList.forEach(e -> {
            final Metrics metrics = e.getValue();
            final List<Long> allDurations = metrics.getRequestDurations();
            final String allDurationsString = allDurations.stream().map(Object::toString).collect(Collectors.joining("<br/>"));
            final OptionalDouble averageOptional = allDurations.stream().mapToLong(value -> value).average();
            double durationAverage = 0;
            if (averageOptional.isPresent())
                durationAverage = averageOptional.getAsDouble() / 1000;
            final NodeAddress nodeAddress = e.getKey();
            final String operator = seedNodeRepository.getOperator(nodeAddress);
            final List<String> errorMessages = metrics.getErrorMessages();
            final int numErrors = (int) errorMessages.stream().filter(s -> !s.isEmpty()).count();
            int numRequests = allDurations.size();
            String lastErrorMsg = "";
            int lastIndexOfError = 0;
            for (int i = 0; i < errorMessages.size(); i++) {
                final String msg = errorMessages.get(i);
                if (!msg.isEmpty()) {
                    lastIndexOfError = i;
                    lastErrorMsg = "Error at request " + lastIndexOfError + ":" + msg;
                }
            }
            //  String lastErrorMsg = numErrors > 0 ? errorMessages.get(errorMessages.size() - 1) : "";
            final List<Map<String, Integer>> allReceivedData = metrics.getReceivedObjectsList();
            Map<String, Integer> lastReceivedData = !allReceivedData.isEmpty() ? allReceivedData.get(allReceivedData.size() - 1) : new HashMap<>();
            final String lastReceivedDataString = lastReceivedData.entrySet().stream().map(Object::toString).collect(Collectors.joining("<br/>"));
            final String allReceivedDataString = allReceivedData.stream().map(Object::toString).collect(Collectors.joining("<br/>"));
            final String requestTs = metrics.getLastDataRequestTs() > 0 ? dateFormat.format(new Date(metrics.getLastDataRequestTs())) : "" + "<br/>";
            final String responseTs = metrics.getLastDataResponseTs() > 0 ? dateFormat.format(new Date(metrics.getLastDataResponseTs())) : "" + "<br/>";
            final String numRequestAttempts = metrics.getNumRequestAttempts() + "<br/>";

            sb.append("\nOperator: ").append(operator)
                    .append("\nNode address: ").append(nodeAddress)
                    .append("\nTotal num requests: ").append(numRequests)
                    .append("\nTotal num errors: ").append(numErrors)
                    .append("\nLast request: ").append(requestTs)
                    .append("\nLast response: ").append(responseTs)
                    .append("\nRRT average: ").append(durationAverage)
                    .append("\nNum requests (retries): ").append(numRequestAttempts)
                    .append("\nLast error message: ").append(lastErrorMsg)
                    .append("\nLast data: ").append(lastReceivedDataString);

            String colorNumErrors = lastIndexOfError == numErrors ? "black" : "red";
            String colorDurationAverage = durationAverage < 30 ? "black" : "red";
            html.append("<tr>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + operator + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + nodeAddress + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + numRequests + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + numErrors + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + requestTs + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + responseTs + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorDurationAverage + "\">" + durationAverage + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + numRequestAttempts + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + lastErrorMsg + "</font> ").append("</td>")
                    .append("<td>").append(lastReceivedDataString).append("</td><td>");

            if (!allReceivedData.isEmpty()) {
                sb.append("\nData deviation last request:\n");
                lastReceivedData.entrySet().stream().forEach(e2 -> {
                    final String dataItem = e2.getKey();
                    double deviation = MathUtils.roundDouble((double) e2.getValue() / averageValues.get(dataItem) * 100, 2);
                    String str = dataItem + ": " + deviation + "%";
                    sb.append(str).append("\n");
                    String color;
                    final double devAbs = Math.abs(deviation - 100);
                    if (devAbs < 5)
                        color = "black";
                    else if (devAbs < 10)
                        color = "blue";
                    else
                        color = "red";

                    html.append("<font color=\"" + color + "\">" + str + "</font>").append("<br/>");

                    if (devAbs >= 20) {
                        if (slackSeedApi != null)
                            slackSeedApi.call(new SlackMessage("Warning: " + nodeAddress.getFullAddress(),
                                    "<" + seedNodeRepository.getOperator(nodeAddress) + ">" + " Your seed node delivers diverging results for " + dataItem + ". " +
                                            "Please check the monitoring status page at http://seedmonitor.0-2-1.net:8080/"));
                    }
                });
                sb.append("Duration all requests: ").append(allDurationsString)
                        .append("\nAll data: ").append(allReceivedDataString).append("\n");

                html.append("</td></tr>");
            }
        });
        html.append("</table>");

        // btc nodes
        sb.append("\n\n####################################\n\nBitcoin nodes\n");
        final long elapsed = new Date().getTime() - btcNodeUptimeTs;
        Set<String> connectedBtcPeers = connectedPeers.stream()
                .map(e -> {
                    String hostname = e.getAddress().getHostname();
                    InetAddress inetAddress = e.getAddress().getAddr();
                    int port = e.getAddress().getPort();
                    if (hostname != null)
                        return hostname + ":" + port;
                    else if (inetAddress != null)
                        return inetAddress.getHostAddress() + ":" + port;
                    else
                        return "";
                })
                .collect(Collectors.toSet());

        List<BtcNodes.BtcNode> onionBtcNodes = new ArrayList<>(btcNodes.getProvidedBtcNodes().stream()
                .filter(BtcNodes.BtcNode::hasOnionAddress)
                .collect(Collectors.toSet()));
        onionBtcNodes.sort((o1, o2) -> o1.getOperator() != null && o2.getOperator() != null ?
                o1.getOperator().compareTo(o2.getOperator()) : 0);

        printTableHeader(html, "Onion");
        printTable(html, sb, onionBtcNodes, connectedBtcPeers, elapsed, true);
        html.append("</tr></table>");

        List<BtcNodes.BtcNode> clearNetNodes = new ArrayList<>(btcNodes.getProvidedBtcNodes().stream()
                .filter(BtcNodes.BtcNode::hasClearNetAddress)
                .collect(Collectors.toSet()));
        clearNetNodes.sort((o1, o2) -> o1.getOperator() != null && o2.getOperator() != null ?
                o1.getOperator().compareTo(o2.getOperator()) : 0);

        printTableHeader(html, "Clear net");
        printTable(html, sb, clearNetNodes, connectedBtcPeers, elapsed, false);
        sb.append("\nConnected Bitcoin nodes: " + connectedBtcPeers + "\n");
        html.append("</tr></table>");
        html.append("<br>Connected Bitcoin nodes: " + connectedBtcPeers + "<br>");
        btcNodeUptimeTs = new Date().getTime();

        html.append("</body></html>");

        resultAsString = sb.toString();
        resultAsHtml = html.toString();
    }

    private void printTableHeader(StringBuilder html, String type) {
        html.append("<br><h3>Bitcoin " + type + " nodes<h3><table style=\"width:100%\">" +
                "<tr>" +
                "<th align=\"left\">Operator</th>" +
                "<th align=\"left\">Domain name</th>" +
                "<th align=\"left\">IP address</th>" +
                "<th align=\"left\">Btc node onion address</th>" +
                "<th align=\"left\">UpTime</th>" +
                "<th align=\"left\">DownTime</th>" +
                "</tr>");
    }

    private void printTable(StringBuilder html, StringBuilder sb, List<BtcNodes.BtcNode> allBtcNodes, Set<String> connectedBtcPeers, long elapsed, boolean isOnion) {
        allBtcNodes.stream().forEach(node -> {
            int upTime = 0;
            int downTime = 0;
            Tuple2<BtcNodes.BtcNode, Boolean> key = new Tuple2<>(node, isOnion);
            if (btcNodeUpTimeMap.containsKey(key))
                upTime = btcNodeUpTimeMap.get(key);

            key = new Tuple2<>(node, isOnion);
            if (btcNodeDownTimeMap.containsKey(key))
                downTime = btcNodeDownTimeMap.get(key);

            boolean isConnected = false;
            // return !connectedBtcPeers.contains(host);
            if (node.hasOnionAddress() && connectedBtcPeers.contains(node.getOnionAddress() + ":" + node.getPort()))
                isConnected = true;

            final String clearNetHost = node.getAddress() != null ? node.getAddress() + ":" + node.getPort() : node.getHostName() + ":" + node.getPort();
            if (node.hasClearNetAddress() && connectedBtcPeers.contains(clearNetHost))
                isConnected = true;

            if (isConnected) {
                upTime += elapsed;
                btcNodeUpTimeMap.put(key, upTime);
            } else {
                downTime += elapsed;
                btcNodeDownTimeMap.put(key, downTime);
            }

            String upTimeString = formatDurationAsWords(upTime, true);
            String downTimeString = formatDurationAsWords(downTime, true);
            String colorNumErrors = isConnected ? "black" : "red";
            html.append("<tr>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + node.getOperator() + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + node.getHostName() + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + node.getAddress() + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + node.getOnionAddress() + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + upTimeString + "</font> ").append("</td>")
                    .append("<td>").append("<font color=\"" + colorNumErrors + "\">" + downTimeString + "</font> ").append("</td>");

            sb.append("\nOperator: ").append(node.getOperator()).append("\n");
            sb.append("Domain name: ").append(node.getHostName()).append("\n");
            sb.append("IP address: ").append(node.getAddress()).append("\n");
            sb.append("Btc node onion address: ").append(node.getOnionAddress()).append("\n");
            sb.append("UpTime: ").append(upTimeString).append("\n");
            sb.append("DownTime: ").append(downTimeString).append("\n");
        });
    }

    public void log() {
        log.info("\n\n#################################################################\n" +
                resultAsString +
                "#################################################################\n\n");
    }

    public static String formatDurationAsWords(long durationMillis, boolean showSeconds) {
        String format;
        String second = Res.get("time.second");
        String minute = Res.get("time.minute");
        String hour = Res.get("time.hour").toLowerCase();
        String day = Res.get("time.day").toLowerCase();
        String days = Res.get("time.days");
        String hours = Res.get("time.hours");
        String minutes = Res.get("time.minutes");
        String seconds = Res.get("time.seconds");
        if (showSeconds) {
            format = "d\' " + days + ", \'H\' " + hours + ", \'m\' " + minutes + ", \'s\' " + seconds + "\'";
        } else
            format = "d\' " + days + ", \'H\' " + hours + ", \'m\' " + minutes + "\'";
        String duration = DurationFormatUtils.formatDuration(durationMillis, format);
        String tmp;
        duration = " " + duration;
        tmp = StringUtils.replaceOnce(duration, " 0 " + days, "");
        if (tmp.length() != duration.length()) {
            duration = tmp;
            tmp = StringUtils.replaceOnce(tmp, " 0 " + hours, "");
            if (tmp.length() != duration.length()) {
                tmp = StringUtils.replaceOnce(tmp, " 0 " + minutes, "");
                duration = tmp;
                if (tmp.length() != tmp.length()) {
                    duration = StringUtils.replaceOnce(tmp, " 0 " + seconds, "");
                }
            }
        }

        if (duration.length() != 0) {
            duration = duration.substring(1);
        }

        tmp = StringUtils.replaceOnce(duration, " 0 " + seconds, "");

        if (tmp.length() != duration.length()) {
            duration = tmp;
            tmp = StringUtils.replaceOnce(tmp, " 0 " + minutes, "");
            if (tmp.length() != duration.length()) {
                duration = tmp;
                tmp = StringUtils.replaceOnce(tmp, " 0 " + hours, "");
                if (tmp.length() != duration.length()) {
                    duration = StringUtils.replaceOnce(tmp, " 0 " + days, "");
                }
            }
        }

        duration = " " + duration;
        duration = StringUtils.replaceOnce(duration, " 1 " + seconds, " 1 " + second);
        duration = StringUtils.replaceOnce(duration, " 1 " + minutes, " 1 " + minute);
        duration = StringUtils.replaceOnce(duration, " 1 " + hours, " 1 " + hour);
        duration = StringUtils.replaceOnce(duration, " 1 " + days, " 1 " + day);
        duration = duration.trim();
        if (duration.equals(","))
            duration = duration.replace(",", "");
        if (duration.startsWith(" ,"))
            duration = duration.replace(" ,", "");
        else if (duration.startsWith(", "))
            duration = duration.replace(", ", "");
        return duration;
    }

    public void addNodesInError(NodeAddress nodeAddress) {
        nodesInError.add(nodeAddress);
    }

    public void removeNodesInError(NodeAddress nodeAddress) {
        nodesInError.remove(nodeAddress);
    }
}
