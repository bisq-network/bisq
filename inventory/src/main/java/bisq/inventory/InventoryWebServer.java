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

import bisq.core.network.p2p.inventory.model.DeviationSeverity;
import bisq.core.network.p2p.inventory.model.InventoryItem;
import bisq.core.network.p2p.inventory.model.RequestInfo;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import java.io.BufferedReader;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;



import spark.Spark;

@Slf4j
public class InventoryWebServer {
    private final static String CLOSE_TAG = "</font><br/>";

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

    public void onNewRequestInfo(Map<NodeAddress, List<RequestInfo>> requestInfoListByNode,
                                 Map<InventoryItem, Double> averageValues,
                                 int requestCounter) {
        this.requestCounter = requestCounter;
        html = generateHtml(requestInfoListByNode, averageValues);
    }

    public void shutDown() {
        Spark.stop();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HTML
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String generateHtml(Map<NodeAddress, List<RequestInfo>> map,
                                Map<InventoryItem, Double> averageValues) {
        StringBuilder html = new StringBuilder();
        html.append("<html>" +
                "<head><style>table, th, td {border: 1px solid black;}</style></head>" +
                "<body><h3>")
                .append("Current time: ").append(new Date().toString()).append("<br/>")
                .append("Request cycle: ").append(requestCounter).append("<br/>")
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
                int numResponses = list.size();
                RequestInfo requestInfo = list.get(numResponses - 1);
                html.append("<td>").append(getSeedNodeInfo(seedNode, requestInfo)).append("</td>")
                        .append("<td>").append(getRequestInfo(requestInfo, numResponses)).append("</td>")
                        .append("<td>").append(getDataInfo(requestInfo, averageValues, map)).append("</td>")
                        .append("<td>").append(getDaoInfo(requestInfo, averageValues, map)).append("</td>")
                        .append("<td>").append(getNetworkInfo(requestInfo, averageValues, map)).append("</td>");
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
        sb.append("Node address: ").append(address).append("<br/>");

        if (requestInfo != null) {
            sb.append("Version: ").append(requestInfo.getDisplayValue(InventoryItem.version)).append("<br/>");
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
        }

        return sb.toString();
    }

    private String getRequestInfo(RequestInfo requestInfo, int numResponses) {
        StringBuilder sb = new StringBuilder();

        DeviationSeverity deviationSeverity = numResponses == requestCounter ?
                DeviationSeverity.OK :
                requestCounter - numResponses > 4 ?
                        DeviationSeverity.ALERT :
                        DeviationSeverity.WARN;
        sb.append("Number of responses: ").append(getColorTagByDeviationSeverity(deviationSeverity))
                .append(numResponses).append(CLOSE_TAG);

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

        String errorMessage = requestInfo.getErrorMessage();
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append("Error message: ").append(getColorTagByDeviationSeverity(DeviationSeverity.ALERT))
                    .append(errorMessage).append(CLOSE_TAG);
        }
        return sb.toString();
    }

    private String getDataInfo(RequestInfo requestInfo,
                               Map<InventoryItem, Double> averageValues,
                               Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine(InventoryItem.OfferPayload, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.MailboxStoragePayload, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.TradeStatistics3, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.AccountAgeWitness, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.SignedWitness, requestInfo, averageValues, map.values()));

        sb.append(getLine(InventoryItem.Alert, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.Filter, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.Mediator, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.RefundAgent, requestInfo, averageValues, map.values()));

        return sb.toString();
    }

    private String getDaoInfo(RequestInfo requestInfo,
                              Map<InventoryItem, Double> averageValues,
                              Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine("Number of BSQ blocks: ", InventoryItem.numBsqBlocks, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.TempProposalPayload, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.ProposalPayload, requestInfo, averageValues, map.values()));
        sb.append(getLine(InventoryItem.BlindVotePayload, requestInfo, averageValues, map.values()));
        sb.append(getLine("DAO state block height: ", InventoryItem.daoStateChainHeight, requestInfo, averageValues, map.values()));

        String daoStateChainHeight = null;
        if (requestInfo.getInventory() != null && requestInfo.getInventory().containsKey(InventoryItem.daoStateChainHeight)) {
            daoStateChainHeight = requestInfo.getInventory().get(InventoryItem.daoStateChainHeight);
        }

        sb.append(getLine("DAO state hash: ", InventoryItem.daoStateHash, requestInfo, averageValues, map.values(), daoStateChainHeight));

        // The hash for proposal changes only at first block of blind vote phase but as we do not want to initialize the
        // dao domain we cannot check that. But we also don't need that as we can just compare that all hashes at all
        // blocks from all seeds are the same. Same for blindVoteHash.
        sb.append(getLine("Proposal state hash: ", InventoryItem.proposalHash, requestInfo, averageValues, map.values(), daoStateChainHeight));
        sb.append(getLine("Blind vote state hash: ", InventoryItem.blindVoteHash, requestInfo, averageValues, map.values(), daoStateChainHeight));

        return sb.toString();
    }

    private String getNetworkInfo(RequestInfo requestInfo,
                                  Map<InventoryItem, Double> averageValues,
                                  Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();

        sb.append(getLine("Max. connections: ", InventoryItem.maxConnections, requestInfo, averageValues, map.values()));
        sb.append(getLine("Number of connections: ", InventoryItem.numConnections, requestInfo, averageValues, map.values()));
        sb.append(getLine("Peak number of connections: ", InventoryItem.peakNumConnections, requestInfo, averageValues, map.values()));
        sb.append(getLine("Number of 'All connections lost' events: ", InventoryItem.numAllConnectionsLostEvents, requestInfo, averageValues, map.values()));

        sb.append(getLine("Sent messages/sec: ", InventoryItem.sentMessagesPerSec, requestInfo,
                averageValues, map.values(), null, this::getRounded));
        sb.append(getLine("Received messages/sec: ", InventoryItem.receivedMessagesPerSec, requestInfo,
                averageValues, map.values(), null, this::getRounded));
        sb.append(getLine("Sent kB/sec: ", InventoryItem.sentBytesPerSec, requestInfo,
                averageValues, map.values(), null, this::getRounded));
        sb.append(getLine("Received kB/sec: ", InventoryItem.receivedBytesPerSec, requestInfo,
                averageValues, map.values(), null, this::getRounded));
        sb.append(getLine("Sent data: ", InventoryItem.sentBytes, requestInfo,
                averageValues, map.values(), null, value -> Utilities.readableFileSize(Long.parseLong(value))));
        sb.append(getLine("Received data: ", InventoryItem.receivedBytes, requestInfo,
                averageValues, map.values(), null, value -> Utilities.readableFileSize(Long.parseLong(value))));
        return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getLine(InventoryItem inventoryItem,
                           RequestInfo requestInfo,
                           Map<InventoryItem, Double> averageValues,
                           Collection<List<RequestInfo>> collection) {
        return getLine(getTitle(inventoryItem),
                inventoryItem,
                requestInfo,
                averageValues,
                collection);
    }

    private String getLine(String title,
                           InventoryItem inventoryItem,
                           RequestInfo requestInfo,
                           Map<InventoryItem, Double> averageValues,
                           Collection<List<RequestInfo>> collection) {
        return getLine(title,
                inventoryItem,
                requestInfo,
                averageValues,
                collection,
                null,
                null);
    }

    private String getLine(String title,
                           InventoryItem inventoryItem,
                           RequestInfo requestInfo,
                           Map<InventoryItem, Double> averageValues,
                           Collection<List<RequestInfo>> collection,
                           @Nullable String daoStateChainHeight) {
        return getLine(title,
                inventoryItem,
                requestInfo,
                averageValues,
                collection,
                daoStateChainHeight,
                null);
    }

    private String getLine(String title,
                           InventoryItem inventoryItem,
                           RequestInfo requestInfo,
                           Map<InventoryItem, Double> averageValues,
                           Collection<List<RequestInfo>> collection,
                           @Nullable String daoStateChainHeight,
                           @Nullable Function<String, String> formatter) {
        String displayValue = requestInfo.getDisplayValue(inventoryItem);
        String value = requestInfo.getValue(inventoryItem);
        if (formatter != null && value != null) {
            displayValue = formatter.apply(value);
        }
        Double deviation = inventoryItem.getDeviation(averageValues, value);
        DeviationSeverity deviationSeverity = inventoryItem.getDeviationSeverity(deviation, collection, value, daoStateChainHeight);
        return title +
                getColorTagByDeviationSeverity(deviationSeverity) +
                displayValue +
                getDeviationAsPercentString(deviation) +
                CLOSE_TAG;
    }

    private String getDeviationAsPercentString(@Nullable Double deviation) {
        if (deviation == null) {
            return "";
        }

        return " (" + MathUtils.roundDouble(100 * deviation, 2) + " %)";
    }

    private String getColorTagByDeviationSeverity(@Nullable DeviationSeverity deviationSeverity) {
        if (deviationSeverity == null) {
            return "<font color=\"black\">";
        }

        switch (deviationSeverity) {
            case WARN:
                return "<font color=\"blue\">";
            case ALERT:
                return "<font color=\"red\">";
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
}
