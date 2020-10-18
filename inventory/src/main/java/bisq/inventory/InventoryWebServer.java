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

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;

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

    public InventoryWebServer(int port,
                              List<NodeAddress> seedNodes,
                              BufferedReader seedNodeFile) {
        this.seedNodes = seedNodes;
        setupOperatorMap(seedNodeFile);

        setupServer(port);
    }

    public void onNewRequestInfo(Map<NodeAddress, List<RequestInfo>> requestInfoListByNode,
                                 Map<InventoryItem, Double> averageValues) {
        html = getHtml(requestInfoListByNode, averageValues);
    }

    private void setupServer(int port) {
        Spark.port(port);
        Spark.get("/", (req, res) -> {
            log.info("Incoming request from: {}", req.userAgent());
            return html == null ? "Starting up..." : html;
        });
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

    private String getHtml(Map<NodeAddress, List<RequestInfo>> map,
                           Map<InventoryItem, Double> averageValues) {
        StringBuilder html = new StringBuilder();
        html.append("<html>" +
                "<head><style>table, th, td {border: 1px solid black;}</style></head>" +
                "<body><h3>")
                .append("Current time: ").append(new Date().toString()).append("<br/>")
                .append("<table style=\"width:100%\">")
                .append("<tr>")
                .append("<th align=\"left\">Seed node info</th>")
                .append("<th align=\"left\">Request info</th>")
                .append("<th align=\"left\">Data inventory</th>")
                .append("<th align=\"left\">DAO data</th>")
                .append("<th align=\"left\">Network info</th>").append("</tr>");

        seedNodes.forEach(seedNode -> {
            if (map.containsKey(seedNode) && !map.get(seedNode).isEmpty()) {
                List<RequestInfo> list = map.get(seedNode);
                int numRequests = list.size();
                RequestInfo last = list.get(numRequests - 1);
                html.append("<tr valign=\"top\">")
                        .append("<td>").append(getSeedNodeInfo(seedNode, last, averageValues)).append("</td>")
                        .append("<td>").append(getRequestInfo(last, numRequests)).append("</td>")
                        .append("<td>").append(getDataInfo(last, averageValues)).append("</td>")
                        .append("<td>").append(getDaoInfo(last, averageValues)).append("</td>")
                        .append("<td>").append(getNetworkInfo(last, averageValues)).append("</td>");
                html.append("</tr>");
            } else {
                html.append("<tr valign=\"top\">")
                        .append("<td>").append(getSeedNodeInfo(seedNode, null, averageValues)).append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>")
                        .append("<td>").append("n/a").append("</td>");
                html.append("</tr>");
            }
        });

        html.append("</table></body></html>");
        return html.toString();
    }


    private String getSeedNodeInfo(NodeAddress nodeAddress,
                                   @Nullable RequestInfo last,
                                   Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();

        String operator = operatorByNodeAddress.get(nodeAddress.getFullAddress());
        sb.append("Operator: ").append(operator).append("<br/>");

        String address = nodeAddress.getFullAddress();
        sb.append("Node address: ").append(address).append("<br/>");

        if (last != null) {
            addInventoryItem("Memory used: ", last, averageValues, sb, InventoryItem.usedMemory);
            addInventoryItem("Node started at: ",
                    last,
                    null,
                    sb,
                    InventoryItem.jvmStartTime,
                    value -> new Date(Long.parseLong(value)).toString());
        }

        return sb.toString();
    }

    private String getRequestInfo(RequestInfo last, int numRequests) {
        StringBuilder sb = new StringBuilder();

        Date requestStartTime = new Date(last.getRequestStartTime());
        sb.append("Requested at: ").append(requestStartTime).append("<br/>");

        Date responseTime = new Date(last.getResponseTime());
        sb.append("Response received at: ").append(responseTime).append("<br/>");

        long rrt = last.getResponseTime() - last.getRequestStartTime();
        DeviationSeverity rrtDeviationSeverity = DeviationSeverity.OK;
        if (rrt > 20_000) {
            rrtDeviationSeverity = DeviationSeverity.ALERT;
        } else if (rrt > 10_000) {
            rrtDeviationSeverity = DeviationSeverity.WARN;
        }

        String rrtString = MathUtils.roundDouble(rrt / 1000d, 3) + " sec";
        sb.append("Round trip time: ").append(getColorTagByDeviationSeverity(rrtDeviationSeverity))
                .append(rrtString).append(CLOSE_TAG);

        sb.append("Number of requests: ").append(getColorTagByDeviationSeverity(DeviationSeverity.OK))
                .append(numRequests).append(CLOSE_TAG);

        String errorMessage = last.getErrorMessage();
        rrtDeviationSeverity = errorMessage == null || errorMessage.isEmpty() ?
                DeviationSeverity.OK :
                DeviationSeverity.WARN;
        sb.append("Error message: ").append(getColorTagByDeviationSeverity(rrtDeviationSeverity))
                .append(errorMessage).append(CLOSE_TAG);

        return sb.toString();
    }


    private String getDataInfo(RequestInfo last,
                               Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();
        addInventoryItem(last, averageValues, sb, InventoryItem.OfferPayload);
        addInventoryItem(last, averageValues, sb, InventoryItem.MailboxStoragePayload);
        addInventoryItem(last, averageValues, sb, InventoryItem.TradeStatistics3);
        addInventoryItem(last, averageValues, sb, InventoryItem.Alert);
        addInventoryItem(last, averageValues, sb, InventoryItem.Filter);
        addInventoryItem(last, averageValues, sb, InventoryItem.Mediator);
        addInventoryItem(last, averageValues, sb, InventoryItem.RefundAgent);
        addInventoryItem(last, averageValues, sb, InventoryItem.AccountAgeWitness);
        addInventoryItem(last, averageValues, sb, InventoryItem.SignedWitness);
        return sb.toString();
    }

    private String getDaoInfo(RequestInfo last,
                              Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();
        addInventoryItem("Number of BSQ blocks: ", last, averageValues, sb, InventoryItem.numBsqBlocks);
        addInventoryItem(last, averageValues, sb, InventoryItem.TempProposalPayload);
        addInventoryItem(last, averageValues, sb, InventoryItem.ProposalPayload);
        addInventoryItem(last, averageValues, sb, InventoryItem.BlindVotePayload);
        addInventoryItem("DAO state block height: ", last, averageValues, sb, InventoryItem.daoStateChainHeight);
        addInventoryItem("DAO state hash: ", last, sb, InventoryItem.daoStateHash);
        addInventoryItem("Proposal state hash: ", last, sb, InventoryItem.proposalHash);
        addInventoryItem("Blind vote state hash: ", last, sb, InventoryItem.blindVoteHash);
        return sb.toString();
    }

    private String getNetworkInfo(RequestInfo last,
                                  Map<InventoryItem, Double> averageValues) {
        StringBuilder sb = new StringBuilder();
        addInventoryItem("Number of connections: ", last, averageValues, sb, InventoryItem.numConnections);
        addInventoryItem("Sent data: ", last, averageValues, sb, InventoryItem.sentData);
        addInventoryItem("Received data: ", last, averageValues, sb, InventoryItem.receivedData);
        addInventoryItem("Received messages/sec: ", last, averageValues, sb, InventoryItem.receivedMessagesPerSec);
        addInventoryItem("Sent messages/sec: ", last, averageValues, sb, InventoryItem.sentMessagesPerSec);
        return sb.toString();
    }

    private void addInventoryItem(RequestInfo last,
                                  Map<InventoryItem, Double> averageValues,
                                  StringBuilder sb,
                                  InventoryItem inventoryItem) {
        addInventoryItem("Number of " + inventoryItem.getKey() + ": ",
                last,
                averageValues,
                sb,
                inventoryItem);
    }

    private void addInventoryItem(String title,
                                  RequestInfo last,
                                  StringBuilder sb,
                                  InventoryItem inventoryItem) {
        addInventoryItem(title,
                last,
                null,
                sb,
                inventoryItem);
    }

    private void addInventoryItem(String title,
                                  RequestInfo last,
                                  @Nullable Map<InventoryItem, Double> averageValues,
                                  StringBuilder sb,
                                  InventoryItem inventoryItem) {
        addInventoryItem(title,
                last,
                averageValues,
                sb,
                inventoryItem,
                null);
    }

    private void addInventoryItem(String title,
                                  RequestInfo last,
                                  @Nullable Map<InventoryItem, Double> averageValues,
                                  StringBuilder sb,
                                  InventoryItem inventoryItem,
                                  @Nullable Function<String, String> formatter) {
        String valueAsString;
        String deviationAsString = "";
        String colorTag = getColorTagByDeviationSeverity(DeviationSeverity.OK);
        if (last.getInventory().containsKey(inventoryItem)) {
            valueAsString = last.getInventory().get(inventoryItem);
            if (averageValues != null && averageValues.containsKey(inventoryItem)) {
                double average = averageValues.get(inventoryItem);
                boolean isNumber = false;
                double value = 0d;
                if (inventoryItem.getType().equals(Integer.class)) {
                    value = Integer.parseInt(valueAsString);
                    isNumber = true;
                } else if (inventoryItem.getType().equals(Long.class)) {
                    value = Long.parseLong(valueAsString);
                    isNumber = true;
                } else if (inventoryItem.getType().equals(Double.class)) {
                    value = Double.parseDouble(valueAsString);
                    isNumber = true;
                }

                if (isNumber) {
                    double deviation = value / average;
                    colorTag = getColorTagByDeviationSeverity(inventoryItem.getDeviationSeverity(deviation));
                    deviationAsString = " (" + MathUtils.roundDouble(100 * deviation, 2) + " %)";
                }
            }
        } else {
            valueAsString = "n/a";
        }

        if (formatter != null) {
            valueAsString = formatter.apply(valueAsString);
        }
        sb.append(title).append(colorTag).append(valueAsString).append(deviationAsString).append(CLOSE_TAG);
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
}
