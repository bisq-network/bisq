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

import bisq.core.network.p2p.inventory.DeviationSeverity;
import bisq.core.network.p2p.inventory.InventoryItem;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import java.io.BufferedReader;

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

    public void onNewRequestInfo(Map<NodeAddress, List<RequestInfo>> requestInfoListByNode,
                                 Map<InventoryItem, Double> averageValues,
                                 int requestCounter) {
        this.requestCounter = requestCounter;
        html = generateHtml(requestInfoListByNode, averageValues);
    }

    public void shutDown() {
        Spark.stop();
    }

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
                html.append("<td>").append(getSeedNodeInfo(seedNode, requestInfo, averageValues)).append("</td>")
                        .append("<td>").append(getRequestInfo(requestInfo, numResponses)).append("</td>")
                        .append("<td>").append(getDataInfo(requestInfo, averageValues, map)).append("</td>")
                        .append("<td>").append(getDaoInfo(requestInfo, averageValues, map)).append("</td>")
                        .append("<td>").append(getNetworkInfo(requestInfo, averageValues)).append("</td>");
            } else {
                html.append("<td>").append(getSeedNodeInfo(seedNode, null, averageValues)).append("</td>")
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

    private String getSeedNodeInfo(NodeAddress nodeAddress,
                                   @Nullable RequestInfo requestInfo,
                                   Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();

        String operator = operatorByNodeAddress.get(nodeAddress.getFullAddress());
        sb.append("Operator: ").append(operator).append("<br/>");

        String address = nodeAddress.getFullAddress();
        sb.append("Node address: ").append(address).append("<br/>");

        if (requestInfo != null) {
            addInventoryItem("Version: ", requestInfo, sb, InventoryItem.version);
            addInventoryItem("Memory used: ", requestInfo, averageValues, sb, InventoryItem.usedMemory,
                    value -> Utilities.readableFileSize(Long.parseLong(value)));
            String jvmStartTimeString = addInventoryItem("Node started at: ",
                    requestInfo,
                    null,
                    sb,
                    InventoryItem.jvmStartTime,
                    value -> new Date(Long.parseLong(value)).toString());

            String duration = jvmStartTimeString != null ?
                    FormattingUtils.formatDurationAsWords(
                            System.currentTimeMillis() - Long.parseLong(jvmStartTimeString),
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

        if (requestInfo.getResponseTime() > 0) {
            long rrt = requestInfo.getResponseTime() - requestInfo.getRequestStartTime();
            DeviationSeverity rrtDeviationSeverity = DeviationSeverity.OK;
            if (rrt > 20_000) {
                rrtDeviationSeverity = DeviationSeverity.ALERT;
            } else if (rrt > 10_000) {
                rrtDeviationSeverity = DeviationSeverity.WARN;
            }
            String rrtString = MathUtils.roundDouble(rrt / 1000d, 3) + " sec";
            sb.append("Round trip time: ").append(getColorTagByDeviationSeverity(rrtDeviationSeverity))
                    .append(rrtString).append(CLOSE_TAG);
        } else {
            sb.append("Round trip time: ").append("n/a").append(CLOSE_TAG);
        }

        Date requestStartTime = new Date(requestInfo.getRequestStartTime());
        sb.append("Requested at: ").append(requestStartTime).append("<br/>");

        String responseTime = requestInfo.getResponseTime() > 0 ?
                new Date(requestInfo.getResponseTime()).toString() :
                "n/a";
        sb.append("Response received at: ").append(responseTime).append("<br/>");

        String errorMessage = requestInfo.getErrorMessage();
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append("Error message: ").append(getColorTagByDeviationSeverity(DeviationSeverity.WARN))
                    .append(errorMessage).append(CLOSE_TAG);
        }
        return sb.toString();
    }

    private String getDataInfo(RequestInfo requestInfo,
                               Map<InventoryItem, Double> averageValues,
                               Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();
        addInventoryItem(requestInfo, averageValues, sb, InventoryItem.OfferPayload);
        addInventoryItem(requestInfo, averageValues, sb, InventoryItem.MailboxStoragePayload);
        addInventoryItem(requestInfo, averageValues, sb, InventoryItem.TradeStatistics3);

        DeviationSeverity deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.Alert, 1, 1);
        addInventoryItem(getTitle(InventoryItem.Alert), requestInfo, averageValues, sb, InventoryItem.Alert,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.Filter, 1, 1);
        addInventoryItem(getTitle(InventoryItem.Filter), requestInfo, averageValues, sb, InventoryItem.Filter,
                null, deviationSeverity);


        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.Mediator, 1, 1);
        addInventoryItem(getTitle(InventoryItem.Mediator), requestInfo, averageValues, sb, InventoryItem.Mediator,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.RefundAgent, 1, 1);
        addInventoryItem(getTitle(InventoryItem.RefundAgent), requestInfo, averageValues, sb, InventoryItem.RefundAgent,
                null, deviationSeverity);

        addInventoryItem(requestInfo, averageValues, sb, InventoryItem.AccountAgeWitness);
        addInventoryItem(requestInfo, averageValues, sb, InventoryItem.SignedWitness);
        return sb.toString();
    }

    private String getDaoInfo(RequestInfo requestInfo,
                              Map<InventoryItem, Double> averageValues,
                              Map<NodeAddress, List<RequestInfo>> map) {
        StringBuilder sb = new StringBuilder();
        DeviationSeverity deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.numBsqBlocks, 1, 3);
        addInventoryItem("Number of BSQ blocks: ", requestInfo, averageValues, sb, InventoryItem.numBsqBlocks,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.TempProposalPayload, 3, 5);
        addInventoryItem(getTitle(InventoryItem.TempProposalPayload), requestInfo, averageValues, sb, InventoryItem.TempProposalPayload,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.ProposalPayload, 1, 2);
        addInventoryItem(getTitle(InventoryItem.ProposalPayload), requestInfo, averageValues, sb, InventoryItem.ProposalPayload,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.BlindVotePayload, 1, 2);
        addInventoryItem(getTitle(InventoryItem.BlindVotePayload), requestInfo, averageValues, sb, InventoryItem.BlindVotePayload,
                null, deviationSeverity);

        deviationSeverity = InventoryUtil.getDeviationSeverityByIntegerDistance(map,
                requestInfo, InventoryItem.daoStateChainHeight, 1, 3);
        String daoStateChainHeightAsString = addInventoryItem("DAO state block height: ", requestInfo,
                averageValues, sb, InventoryItem.daoStateChainHeight, null, deviationSeverity);

        DeviationSeverity daoStateHashDeviationSeverity = InventoryUtil.getDeviationSeverityForHash(map,
                daoStateChainHeightAsString,
                requestInfo,
                InventoryItem.daoStateHash);
        addInventoryItem("DAO state hash: ", requestInfo, null, sb,
                InventoryItem.daoStateHash, null, daoStateHashDeviationSeverity);

        // The hash for proposal changes only at first block of blind vote phase but as we do not want to initialize the
        // dao domain we cannot check that. But we also don't need that as we can just compare that all hashes at all
        // blocks from all seeds are the same. Same for blindVoteHash.

        DeviationSeverity proposalHashDeviationSeverity = InventoryUtil.getDeviationSeverityForHash(map,
                daoStateChainHeightAsString,
                requestInfo,
                InventoryItem.proposalHash);
        addInventoryItem("Proposal state hash: ", requestInfo, null, sb,
                InventoryItem.proposalHash, null, proposalHashDeviationSeverity);

        DeviationSeverity blindVoteHashDeviationSeverity = InventoryUtil.getDeviationSeverityForHash(map,
                daoStateChainHeightAsString,
                requestInfo,
                InventoryItem.blindVoteHash);
        addInventoryItem("Blind vote state hash: ", requestInfo, null, sb,
                InventoryItem.blindVoteHash, null, blindVoteHashDeviationSeverity);

        return sb.toString();
    }

    private String getNetworkInfo(RequestInfo requestInfo,
                                  Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();
        addInventoryItem("Max. connections: ", requestInfo, averageValues, sb, InventoryItem.maxConnections);
        addInventoryItem("Number of connections: ", requestInfo, averageValues, sb, InventoryItem.numConnections);

        addInventoryItem("Sent messages/sec: ", requestInfo, averageValues, sb, InventoryItem.sentMessagesPerSec,
                value -> String.valueOf(MathUtils.roundDouble(Double.parseDouble(value), 2)));
        addInventoryItem("Received messages/sec: ", requestInfo, averageValues, sb, InventoryItem.receivedMessagesPerSec,
                value -> String.valueOf(MathUtils.roundDouble(Double.parseDouble(value), 2)));
        addInventoryItem("Sent kB/sec: ", requestInfo, averageValues, sb, InventoryItem.sentBytesPerSec,
                value -> String.valueOf(MathUtils.roundDouble(Double.parseDouble(value) / 1024, 2)));
        addInventoryItem("Received kB/sec: ", requestInfo, averageValues, sb, InventoryItem.receivedBytesPerSec,
                value -> String.valueOf(MathUtils.roundDouble(Double.parseDouble(value) / 1024, 2)));
        addInventoryItem("Sent data: ", requestInfo, averageValues, sb, InventoryItem.sentBytes,
                value -> Utilities.readableFileSize(Long.parseLong(value)));
        addInventoryItem("Received data: ", requestInfo, averageValues, sb, InventoryItem.receivedBytes,
                value -> Utilities.readableFileSize(Long.parseLong(value)));
        return sb.toString();
    }

    private String addInventoryItem(RequestInfo requestInfo,
                                    Map<InventoryItem, Double> averageValues,
                                    StringBuilder sb,
                                    InventoryItem inventoryItem) {
        return addInventoryItem(getTitle(inventoryItem),
                requestInfo,
                averageValues,
                sb,
                inventoryItem);
    }

    private String addInventoryItem(String title,
                                    RequestInfo requestInfo,
                                    StringBuilder sb,
                                    InventoryItem inventoryItem) {
        return addInventoryItem(title,
                requestInfo,
                null,
                sb,
                inventoryItem);
    }

    private String addInventoryItem(String title,
                                    RequestInfo requestInfo,
                                    @Nullable Map<InventoryItem, Double> averageValues,
                                    StringBuilder sb,
                                    InventoryItem inventoryItem) {
        return addInventoryItem(title,
                requestInfo,
                averageValues,
                sb,
                inventoryItem,
                null,
                null);
    }

    private String addInventoryItem(String title,
                                    RequestInfo requestInfo,
                                    @Nullable Map<InventoryItem, Double> averageValues,
                                    StringBuilder sb,
                                    InventoryItem inventoryItem,
                                    @Nullable Function<String, String> formatter) {
        return addInventoryItem(title,
                requestInfo,
                averageValues,
                sb,
                inventoryItem,
                formatter,
                null);
    }

    private String addInventoryItem(String title,
                                    RequestInfo requestInfo,
                                    @Nullable Map<InventoryItem, Double> averageValues,
                                    StringBuilder sb,
                                    InventoryItem inventoryItem,
                                    @Nullable Function<String, String> formatter,
                                    @Nullable DeviationSeverity deviationSeverity) {
        String valueAsString = null;
        String displayString = "n/a";
        String deviationAsString = "";
        String colorTag = getColorTagByDeviationSeverity(DeviationSeverity.OK);
        Map<InventoryItem, String> inventory = requestInfo.getInventory();
        if (inventory != null && inventory.containsKey(inventoryItem)) {
            valueAsString = inventory.get(inventoryItem);
            if (averageValues != null && averageValues.containsKey(inventoryItem)) {
                double average = averageValues.get(inventoryItem);
                double deviation = 0;
                if (inventoryItem.getType().equals(Integer.class)) {
                    int value = Integer.parseInt(valueAsString);
                    deviation = value / average;
                } else if (inventoryItem.getType().equals(Long.class)) {
                    long value = Long.parseLong(valueAsString);
                    deviation = value / average;
                } else if (inventoryItem.getType().equals(Double.class)) {
                    double value = Double.parseDouble(valueAsString);
                    deviation = value / average;
                }

                if (!inventoryItem.getType().equals(String.class)) {
                    colorTag = getColorTagByDeviationSeverity(inventoryItem.getDeviationSeverity(deviation));
                    deviationAsString = " (" + MathUtils.roundDouble(100 * deviation, 2) + " %)";
                }
            }

            if (deviationSeverity != null) {
                colorTag = getColorTagByDeviationSeverity(deviationSeverity);
            }

            // We only do formatting if we have any value
            if (formatter != null) {
                displayString = formatter.apply(valueAsString);
            } else {
                displayString = valueAsString;
            }
        }

        sb.append(title).append(colorTag).append(displayString).append(deviationAsString).append(CLOSE_TAG);
        return valueAsString;
    }

    private String getColorTagByDeviationSeverity(DeviationSeverity deviationSeverity) {
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

    private String getTitle(InventoryItem inventoryItem) {
        return "Number of " + inventoryItem.getKey() + ": ";
    }
}
