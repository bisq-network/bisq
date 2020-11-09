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

package bisq.inventory;

import bisq.core.network.p2p.inventory.model.DeviationByIntegerDiff;
import bisq.core.network.p2p.inventory.model.DeviationByPercentage;
import bisq.core.network.p2p.inventory.model.DeviationSeverity;
import bisq.core.network.p2p.inventory.model.InventoryItem;
import bisq.core.network.p2p.inventory.model.RequestInfo;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import com.google.common.base.Joiner;

import java.io.BufferedReader;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;



import spark.Spark;

@Slf4j
public class InventoryWebServer {
    private final static String CLOSE_TAG = "</font><br/>";
    private final static String WARNING_ICON = "&#9888; ";
    private final static String ALERT_ICON = "&#9760; "; // &#9889;  &#9889;

    private final List<NodeAddress> seedNodes;
    private final Map<String, String> operatorByNodeAddress = new HashMap<>();

    private String html;
    private int requestCounter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InventoryWebServer(int port,
                              List<NodeAddress> seedNodes,
                              BufferedReader seedNodeFile) {
        this.seedNodes = seedNodes;
        setupOperatorMap(seedNodeFile);

        Spark.port(port);
        Spark.get("/", (req, res) -> {
            log.info("Incoming request from: {}", req.userAgent());
            return html == null ? "Starting up..." : html;
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onNewRequestInfo(Map<NodeAddress, List<RequestInfo>> requestInfoListByNode, int requestCounter) {
        this.requestCounter = requestCounter;
        html = generateHtml(requestInfoListByNode);
    }

    public void shutDown() {
        Spark.stop();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HTML
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String generateHtml(Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder html = new StringBuilder();
        html.append("<html>" +
                "<head>" +
                "<style type=\"text/css\">" +
                "   a {" +
                "      text-decoration:none; color: black;" +
                "   }" +
                " #warn { color: #ff7700; } " +
                " #alert { color: #ff0000; } " +
                "table, th, td {border: 1px solid black;}" +
                "</style></head>" +
                "<body><h3>")
                .append("Current time: ").append(new Date().toString()).append("<br/>")
                .append("Request cycle: ").append(requestCounter).append("<br/>")
                .append("Version/commit: ").append(Version.VERSION).append(" / ").append(RequestInfo.COMMIT_HASH).append("<br/>")
                .append("<table style=\"width:100%\">")
                .append("<tr>")
                .append("<th align=\"left\">Seed node info</th>")
                .append("<th align=\"left\">Request info</th>")
                .append("<th align=\"left\">Data inventory</th>")
                .append("<th align=\"left\">DAO data</th>")
                .append("<th align=\"left\">Network info</th>").append("</tr>");

        seedNodes.forEach(seedNode -> {
            html.append("<tr valign=\"top\">");
            if (map.containsKey(seedNode) && !map.get(seedNode).isEmpty()) {
                List<RequestInfo> list = map.get(seedNode);
                int numRequests = list.size();
                RequestInfo requestInfo = list.get(numRequests - 1);
                html.append("<td>").append(getSeedNodeInfo(seedNode, requestInfo)).append("</td>")
                        .append("<td>").append(getRequestInfo(seedNode, requestInfo, numRequests, map)).append("</td>")
                        .append("<td>").append(getDataInfo(seedNode, requestInfo, map)).append("</td>")
                        .append("<td>").append(getDaoInfo(seedNode, requestInfo, map)).append("</td>")
                        .append("<td>").append(getNetworkInfo(seedNode, requestInfo, map)).append("</td>");
            } else {
                html.append("<td>").append(getSeedNodeInfo(seedNode, null)).append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>");
            }
            html.append("</tr>");
        });

        html.append("</table></body></html>");
        return html.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sub sections
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getSeedNodeInfo(NodeAddress nodeAddress,
                                   @Nullable RequestInfo requestInfo) {
        StringBuilder sb = new StringBuilder();

        String operator = operatorByNodeAddress.get(nodeAddress.getFullAddress());
        sb.append("Operator: ").append(operator).append("<br/>");

        String address = nodeAddress.getFullAddress();

        String filteredSeeds = requestInfo != null ? requestInfo.getValue(InventoryItem.filteredSeeds) : null;
        if (filteredSeeds != null && filteredSeeds.contains(address)) {
            sb.append(getColorTagByDeviationSeverity(DeviationSeverity.ALERT)).append("Node address: ")
                    .append(address).append(" (is filtered!)").append(CLOSE_TAG);
        } else {
            sb.append("Node address: ").append(address).append("<br/>");
        }

        if (requestInfo != null) {
            sb.append("Version: ").append(requestInfo.getDisplayValue(InventoryItem.version)).append("<br/>");
            sb.append("Commit hash: ").append(requestInfo.getDisplayValue(InventoryItem.commitHash)).append("<br/>");
            String memory = requestInfo.getValue(InventoryItem.usedMemory);
            String memoryString = memory != null ? Utilities.readableFileSize(Long.parseLong(memory)) : "n/a";
            sb.append("Memory used: ")
                    .append(memoryString)
                    .append("<br/>");

            String jvmStartTimeString = requestInfo.getValue(InventoryItem.jvmStartTime);
            long jvmStartTime = jvmStartTimeString != null ? Long.parseLong(jvmStartTimeString) : 0;
            sb.append("Node started at: ")
                    .append(new Date(jvmStartTime).toString())
                    .append("<br/>");

            String duration = jvmStartTime > 0 ?
                    FormattingUtils.formatDurationAsWords(System.currentTimeMillis() - jvmStartTime,
                            true, true) :
                    "n/a";
            sb.append("Run duration: ").append(duration).append("<br/>");

            String filteredSeedNodes = requestInfo.getDisplayValue(InventoryItem.filteredSeeds)
                    .replace(System.getProperty("line.separator"), "<br/>");
            if (filteredSeedNodes.isEmpty()) {
                filteredSeedNodes = "-";
            }
            sb.append("Filtered seed nodes: ")
                    .append(filteredSeedNodes)
                    .append("<br/>");
        }

        return sb.toString();
    }

    private String getRequestInfo(NodeAddress seedNode,
                                  RequestInfo requestInfo,
                                  int numRequests,
                                  Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        DeviationSeverity deviationSeverity = numRequests == requestCounter ?
                DeviationSeverity.OK :
                requestCounter - numRequests > 4 ?
                        DeviationSeverity.ALERT :
                        DeviationSeverity.WARN;
        sb.append("Number of requests: ").append(getColorTagByDeviationSeverity(deviationSeverity))
                .append(numRequests).append(CLOSE_TAG);

        DeviationSeverity rrtDeviationSeverity = DeviationSeverity.OK;
        String rrtString = "n/a";
        if (requestInfo.getResponseTime() > 0) {
            long rrt = requestInfo.getResponseTime() - requestInfo.getRequestStartTime();
            if (rrt > 20_000) {
                rrtDeviationSeverity = DeviationSeverity.ALERT;
            } else if (rrt > 10_000) {
                rrtDeviationSeverity = DeviationSeverity.WARN;
            }
            rrtString = MathUtils.roundDouble(rrt / 1000d, 3) + " sec";

        }
        sb.append("Round trip time: ").append(getColorTagByDeviationSeverity(rrtDeviationSeverity))
                .append(rrtString).append(CLOSE_TAG);

        Date requestStartTime = new Date(requestInfo.getRequestStartTime());
        sb.append("Requested at: ").append(requestStartTime).append("<br/>");

        String responseTime = requestInfo.getResponseTime() > 0 ?
                new Date(requestInfo.getResponseTime()).toString() :
                "n/a";
        sb.append("Response received at: ").append(responseTime).append("<br/>");

        sb.append(getErrorMsgLine(seedNode, requestInfo, map));
        return sb.toString();
    }

    private String getDataInfo(NodeAddress seedNode,
                               RequestInfo requestInfo,
                               Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine(InventoryItem.OfferPayload, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.MailboxStoragePayload, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.TradeStatistics3, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.AccountAgeWitness, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.SignedWitness, seedNode, requestInfo, map));

        sb.append(getLine(InventoryItem.Alert, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.Filter, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.Mediator, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.RefundAgent, seedNode, requestInfo, map));

        return sb.toString();
    }

    private String getDaoInfo(NodeAddress seedNode,
                              RequestInfo requestInfo,
                              Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine("Number of BSQ blocks: ", InventoryItem.numBsqBlocks, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.TempProposalPayload, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.ProposalPayload, seedNode, requestInfo, map));
        sb.append(getLine(InventoryItem.BlindVotePayload, seedNode, requestInfo, map));
        sb.append(getLine("DAO state block height: ", InventoryItem.daoStateChainHeight, seedNode, requestInfo, map));

        sb.append(getLine("DAO state hash: ", InventoryItem.daoStateHash, seedNode, requestInfo, map));

        // The hash for proposal changes only at first block of blind vote phase but as we do not want to initialize the
        // dao domain we cannot check that. But we also don't need that as we can just compare that all hashes at all
        // blocks from all seeds are the same. Same for blindVoteHash.
        sb.append(getLine("Proposal state hash: ", InventoryItem.proposalHash, seedNode, requestInfo, map));
        sb.append(getLine("Blind vote state hash: ", InventoryItem.blindVoteHash, seedNode, requestInfo, map));

        return sb.toString();
    }

    private String getNetworkInfo(NodeAddress seedNode,
                                  RequestInfo requestInfo,
                                  Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine("Max. connections: ",
                InventoryItem.maxConnections, seedNode, requestInfo, map));
        sb.append(getLine("Number of connections: ",
                InventoryItem.numConnections, seedNode, requestInfo, map));
        sb.append(getLine("Peak number of connections: ",
                InventoryItem.peakNumConnections, seedNode, requestInfo, map));
        sb.append(getLine("Number of 'All connections lost' events: ",
                InventoryItem.numAllConnectionsLostEvents, seedNode, requestInfo, map));

        sb.append(getLine("Sent messages/sec: ",
                InventoryItem.sentMessagesPerSec, seedNode, requestInfo, map, this::getRounded));
        sb.append(getLine("Received messages/sec: ",
                InventoryItem.receivedMessagesPerSec, seedNode, requestInfo, map, this::getRounded));
        sb.append(getLine("Sent kB/sec: ",
                InventoryItem.sentBytesPerSec, seedNode, requestInfo, map, this::getKbRounded));
        sb.append(getLine("Received kB/sec: ",
                InventoryItem.receivedBytesPerSec, seedNode, requestInfo, map, this::getKbRounded));
        sb.append(getLine("Sent data: ",
                InventoryItem.sentBytes, seedNode, requestInfo, map,
                value -> Utilities.readableFileSize(Long.parseLong(value))));
        sb.append(getLine("Received data: ",
                InventoryItem.receivedBytes, seedNode, requestInfo, map,
                value -> Utilities.readableFileSize(Long.parseLong(value))));
        return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getLine(InventoryItem inventoryItem,
                           NodeAddress seedNode,
                           RequestInfo requestInfo,
                           Map<NodeAddress, List<RequestInfo>> map) {
        return getLine(getTitle(inventoryItem),
                inventoryItem,
                seedNode,
                requestInfo,
                map);
    }

    private String getLine(String title,
                           InventoryItem inventoryItem,
                           NodeAddress seedNode,
                           RequestInfo requestInfo,
                           Map<NodeAddress, List<RequestInfo>> map) {
        return getLine(title,
                inventoryItem,
                seedNode,
                requestInfo,
                map,
                null);
    }

    private String getLine(String title,
                           InventoryItem inventoryItem,
                           NodeAddress seedNode,
                           RequestInfo requestInfo,
                           Map<NodeAddress, List<RequestInfo>> map,
                           @Nullable Function<String, String> formatter) {
        String displayValue = requestInfo.getDisplayValue(inventoryItem);
        String value = requestInfo.getValue(inventoryItem);
        if (formatter != null && value != null) {
            displayValue = formatter.apply(value);
        }

        String deviationAsPercentString = "";
        DeviationSeverity deviationSeverity = DeviationSeverity.OK;
        if (requestInfo.getDataMap().containsKey(inventoryItem)) {
            RequestInfo.Data data = requestInfo.getDataMap().get(inventoryItem);
            deviationAsPercentString = getDeviationAsPercentString(inventoryItem, data);
            deviationSeverity = data.getDeviationSeverity();
        }

        List<RequestInfo> requestInfoList = map.get(seedNode);
        String historicalWarnings = "";
        String historicalAlerts = "";
        List<String> warningsAtRequestNumber = new ArrayList<>();
        List<String> alertsAtRequestNumber = new ArrayList<>();
        if (requestInfoList != null) {
            for (int i = 0; i < requestInfoList.size(); i++) {
                RequestInfo reqInfo = requestInfoList.get(i);
                Map<InventoryItem, RequestInfo.Data> deviationInfoMap = reqInfo.getDataMap();
                if (deviationInfoMap.containsKey(inventoryItem)) {
                    RequestInfo.Data data = deviationInfoMap.get(inventoryItem);
                    String deviationAsPercent = getDeviationAsPercentString(inventoryItem, data);
                    if (data.isPersistentWarning()) {
                        warningsAtRequestNumber.add((i + 1) + deviationAsPercent);
                    } else if (data.isPersistentAlert()) {
                        alertsAtRequestNumber.add((i + 1) + deviationAsPercent);
                    }
                }
            }

            if (!warningsAtRequestNumber.isEmpty()) {
                historicalWarnings = warningsAtRequestNumber.size() + " repeated warning(s) at request(s) " +
                        Joiner.on(", ").join(warningsAtRequestNumber);
            }
            if (!alertsAtRequestNumber.isEmpty()) {
                historicalAlerts = alertsAtRequestNumber.size() + " repeated alert(s) at request(s): " +
                        Joiner.on(", ").join(alertsAtRequestNumber);
            }
        }
        String historicalWarningsHtml = warningsAtRequestNumber.isEmpty() ? "" :
                ", <b><a id=\"warn\" href=\"#\" title=\"" + historicalWarnings + "\">" + WARNING_ICON +
                        warningsAtRequestNumber.size() + "</a></b>";
        String historicalAlertsHtml = alertsAtRequestNumber.isEmpty() ? "" :
                ", <b><a id=\"alert\" href=\"#\" title=\"" + historicalAlerts + "\">" + ALERT_ICON +
                        alertsAtRequestNumber.size() + "</a></b>";

        return title +
                getColorTagByDeviationSeverity(deviationSeverity) +
                displayValue +
                deviationAsPercentString +
                historicalWarningsHtml +
                historicalAlertsHtml +
                CLOSE_TAG;
    }

    private String getDeviationAsPercentString(InventoryItem inventoryItem, RequestInfo.Data data) {
        Double deviation = data.getDeviation();
        if (deviation == null || deviation == 1) {
            return "";
        }
        if (inventoryItem.getDeviationType() instanceof DeviationByPercentage) {
            return getDeviationInRoundedPercent(deviation);
        } else if (inventoryItem.getDeviationType() instanceof DeviationByIntegerDiff) {
            // For larger numbers like chain height we need to show all decimals as diff can be very small
            return getDeviationInExactPercent(deviation);
        } else {
            return "";
        }
    }

    private String getDeviationInRoundedPercent(double deviation) {
        return " (" + MathUtils.roundDouble(100 * deviation, 2) + " %)";
    }

    private String getDeviationInExactPercent(double deviation) {
        return " (" + 100 * deviation + " %)";
    }

    private String getColorTagByDeviationSeverity(@Nullable DeviationSeverity deviationSeverity) {
        if (deviationSeverity == null) {
            return "<font color=\"black\">";
        }

        switch (deviationSeverity) {
            case WARN:
                return "<font color=\"#0000cc\">";
            case ALERT:
                return "<font color=\"#cc0000\">";
            case IGNORED:
                return "<font color=\"#333333\">";
            case OK:
            default:
                return "<font color=\"black\">";
        }
    }

    private String getTitle(InventoryItem inventoryItem) {
        return "Number of " + inventoryItem.getKey() + ": ";
    }

    private String getRounded(String value) {
        return String.valueOf(MathUtils.roundDouble(Double.parseDouble(value), 2));
    }

    private String getKbRounded(String bytes) {
        return String.valueOf(MathUtils.roundDouble(Double.parseDouble(bytes) / 1000, 2));
    }

    private void setupOperatorMap(BufferedReader seedNodeFile) {
        seedNodeFile.lines().forEach(line -> {
            if (!line.startsWith("#")) {
                String[] strings = line.split(" \\(@");
                String node = strings.length > 0 ? strings[0] : "n/a";
                String operator = strings.length > 1 ? strings[1].replace(")", "") : "n/a";
                operatorByNodeAddress.put(node, operator);
            }
        });
    }

    // We use here a bit diff. model as with other historical data alerts/warnings as we do not store it in the data
    // object as we do with normal inventoryItems. So the historical error msg are not available in the json file.
    // If we need it we have to move that handling here to the InventoryMonitor and change the data model to support the
    // missing data for error messages.
    private String getErrorMsgLine(NodeAddress seedNode,
                                   RequestInfo requestInfo,
                                   Map<NodeAddress, List<RequestInfo>> map) {
        String errorMessage = requestInfo.hasError() ? requestInfo.getErrorMessage() : "-";
        List<RequestInfo> requestInfoList = map.get(seedNode);
        List<String> errorsAtRequestNumber = new ArrayList<>();
        String historicalErrorsHtml = "";
        if (requestInfoList != null) {
            for (int i = 0; i < requestInfoList.size(); i++) {
                RequestInfo requestInfo1 = requestInfoList.get(i);

                // We ignore old errors as at startup timeouts are expected and each node restarts once a day
                long duration = System.currentTimeMillis() - requestInfo1.getRequestStartTime();
                if (requestInfo1.getRequestStartTime() > 0 && duration > TimeUnit.HOURS.toMillis(24)) {
                    continue;
                }

                if (requestInfo1.hasError()) {
                    errorsAtRequestNumber.add((i + 1) + " (" + requestInfo1.getErrorMessage() + ")");
                }
            }

            if (!errorsAtRequestNumber.isEmpty()) {
                String errorIcon;
                String type;
                String style;
                if (errorsAtRequestNumber.size() > 4) {
                    errorIcon = ALERT_ICON;
                    type = "alert";
                    style = "alert";
                } else {
                    errorIcon = WARNING_ICON;
                    type = "warning";
                    style = "warn";
                }
                String historicalAlerts = errorsAtRequestNumber.size() + " repeated " + type + "(s) at request(s): " +
                        Joiner.on(", ").join(errorsAtRequestNumber);
                historicalErrorsHtml = errorsAtRequestNumber.isEmpty() ? "" :
                        ", <b><a id=\"" + style + "\" href=\"#\" title=\"" + historicalAlerts + "\">" + errorIcon +
                                errorsAtRequestNumber.size() + "</a></b>";
            }
        }
        DeviationSeverity deviationSeverity = requestInfo.hasError() ?
                errorsAtRequestNumber.size() > 4 ? DeviationSeverity.ALERT : DeviationSeverity.WARN
                : DeviationSeverity.OK;

        return "Error message: " +
                getColorTagByDeviationSeverity(deviationSeverity) +
                errorMessage +
                historicalErrorsHtml +
                CLOSE_TAG;
    }
}
