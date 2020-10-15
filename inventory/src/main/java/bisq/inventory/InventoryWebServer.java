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

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;

import java.io.BufferedReader;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;



import spark.Spark;

@Slf4j
public class InventoryWebServer {
    private final List<NodeAddress> seedNodes;
    private Map<NodeAddress, List<InventoryMonitor.RequestInfo>> map = new HashMap<>();
    private final Map<String, String> operatorByNodeAddress = new HashMap<>();
    private String html;

    public InventoryWebServer(int port,
                              List<NodeAddress> seedNodes,
                              BufferedReader seedNodeFile) {
        this.seedNodes = seedNodes;
        setupOperatorMap(seedNodeFile);

        setupServer(port);
    }

    public void onNewRequestInfo(InventoryMonitor.RequestInfo requestInfo, NodeAddress nodeAddress) {
        map.putIfAbsent(nodeAddress, new ArrayList<>());
        map.get(nodeAddress).add(requestInfo);
        html = getHtml(map);
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

    private String getHtml(Map<NodeAddress, List<InventoryMonitor.RequestInfo>> map) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>table, th, td {border: 1px solid black;}</style></head><body><h3>")
                .append("<table style=\"width:100%\">")
                .append("<tr>")
                .append("<th align=\"left\">Seed node info</th>")
                .append("<th align=\"left\">Request info</th>")
                .append("<th align=\"left\">Data inventory</th>")
                .append("<th align=\"left\">DAO data</th>")
                .append("<th align=\"left\">Network info</th>").append("</tr>");

        seedNodes.forEach(seedNode -> {
            if (map.containsKey(seedNode) && !map.get(seedNode).isEmpty()) {
                List<InventoryMonitor.RequestInfo> list = map.get(seedNode);
                int numRequests = list.size();
                InventoryMonitor.RequestInfo last = list.get(numRequests - 1);
                html.append("<tr valign=\"top\">")
                        .append("<td>").append(getSeedNodeInfo(seedNode, last)).append("</td>")
                        .append("<td>").append(getRequestInfo(last, numRequests)).append("</td>")
                        .append("<td>").append(getDataInfo(last)).append("</td>")
                        .append("<td>").append(getDaoInfo(last)).append("</td>")
                        .append("<td>").append(getNetworkInfo(last)).append("</td>");
                html.append("</tr>");
            } else {
                html.append("<tr valign=\"top\">")
                        .append("<td>").append(getSeedNodeInfo(seedNode, null)).append("</td>")
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


    private String getSeedNodeInfo(NodeAddress nodeAddress, @Nullable InventoryMonitor.RequestInfo last) {
        StringBuilder sb = new StringBuilder();

        String operator = operatorByNodeAddress.get(nodeAddress.getFullAddress());
        sb.append("Operator: ").append(operator).append("<br/>");

        String address = nodeAddress.getFullAddress();
        sb.append("Node address: ").append(address).append("<br/>");

        if (last != null) {
            String usedMemory = last.getInventory().get("usedMemory");
            sb.append("Memory used: ").append(usedMemory).append(" MB<br/>");

            Date jvmStartTime = new Date(Long.parseLong(last.getInventory().get("jvmStartTime")));
            sb.append("Node started at: ").append(jvmStartTime).append("<br/>");
        }

        return sb.toString();
    }

    private String getRequestInfo(InventoryMonitor.RequestInfo last, int numRequests) {
        StringBuilder sb = new StringBuilder();

        Date requestStartTime = new Date(last.getRequestStartTime());
        sb.append("Requested at: ").append(requestStartTime).append("<br/>");

        Date responseTime = new Date(last.getResponseTime());
        sb.append("Response received at: ").append(responseTime).append("<br/>");

        long durationAsLong = last.getResponseTime() - last.getRequestStartTime();
        double rrt = MathUtils.roundDouble(durationAsLong / 1000d, 3);
        sb.append("Round trip time: ").append(rrt).append(" sec<br/>");

        sb.append("Number of requests: ").append(numRequests).append("<br/>");

        sb.append("Error message: ").append(last.getErrorMessage()).append("<br/>");

        return sb.toString();
    }

    private String getDataInfo(InventoryMonitor.RequestInfo last) {
        StringBuilder sb = new StringBuilder();

        String OfferPayload = last.getInventory().get("OfferPayload");
        sb.append("Number of OfferPayload: ").append(OfferPayload).append("<br/>");

        String MailboxStoragePayload = last.getInventory().get("MailboxStoragePayload");
        sb.append("Number of MailboxStoragePayload: ").append(MailboxStoragePayload).append("<br/>");

        String TradeStatistics3 = last.getInventory().get("TradeStatistics3");
        sb.append("Number of TradeStatistics3: ").append(TradeStatistics3).append("<br/>");

        String Alert = last.getInventory().get("Alert");
        sb.append("Number of Alert: ").append(Alert).append("<br/>");

        String Filter = last.getInventory().get("Filter");
        sb.append("Number of Filter: ").append(Filter).append("<br/>");

        String Mediator = last.getInventory().get("Mediator");
        sb.append("Number of Mediator: ").append(Mediator).append("<br/>");

        String RefundAgent = last.getInventory().get("RefundAgent");
        sb.append("Number of RefundAgent: ").append(RefundAgent).append("<br/>");

        String AccountAgeWitness = last.getInventory().get("AccountAgeWitness");
        sb.append("Number of AccountAgeWitness: ").append(AccountAgeWitness).append("<br/>");

        String SignedWitness = last.getInventory().get("SignedWitness");
        sb.append("Number of SignedWitness: ").append(SignedWitness).append("<br/>");

        return sb.toString();
    }

    private String getDaoInfo(InventoryMonitor.RequestInfo last) {
        StringBuilder sb = new StringBuilder();

        String numBsqBlocks = last.getInventory().get("numBsqBlocks");
        sb.append("Number of BSQ blocks: ").append(numBsqBlocks).append("<br/>");

        String TempProposalPayload = last.getInventory().get("TempProposalPayload");
        sb.append("Number of TempProposalPayload: ").append(TempProposalPayload).append("<br/>");

        String ProposalPayload = last.getInventory().get("ProposalPayload");
        sb.append("Number of ProposalPayload: ").append(ProposalPayload).append("<br/>");

        String BlindVotePayload = last.getInventory().get("BlindVotePayload");
        sb.append("Number of BlindVotePayload: ").append(BlindVotePayload).append("<br/>");

        String daoStateChainHeight = last.getInventory().get("daoStateChainHeight");
        sb.append("DAO state block height: ").append(daoStateChainHeight).append("<br/>");

        String daoStateHash = last.getInventory().get("daoStateHash");
        sb.append("DAO state hash: ").append(daoStateHash).append("<br/>");

        String proposalHash = last.getInventory().get("proposalHash");
        sb.append("Proposal state hash: ").append(proposalHash).append("<br/>");

        String blindVoteHash = last.getInventory().get("blindVoteHash");
        sb.append("Blind vote state hash: ").append(blindVoteHash).append("<br/>");

        return sb.toString();
    }

    private String getNetworkInfo(InventoryMonitor.RequestInfo last) {
        StringBuilder sb = new StringBuilder();

        String numConnections = last.getInventory().get("numConnections");
        sb.append("Number of connections: ").append(numConnections).append("<br/>");

        String sentBytes = last.getInventory().get("sentData");
        sb.append("Sent data: ").append(sentBytes).append("<br/>");

        String receivedBytes = last.getInventory().get("receivedData");
        sb.append("Received data: ").append(receivedBytes).append("<br/>");

        String receivedMessagesPerSec = last.getInventory().get("receivedMessagesPerSec");
        sb.append("Received messages/sec: ").append(receivedMessagesPerSec).append("<br/>");

        String sentMessagesPerSec = last.getInventory().get("sentMessagesPerSec");
        sb.append("Sent messages/sec: ").append(sentMessagesPerSec).append("<br/>");

        return sb.toString();
    }

}
