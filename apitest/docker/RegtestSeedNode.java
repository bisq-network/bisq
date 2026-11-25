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

import bisq.seednode.SeedNodeMain;

import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.common.UserThread;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Read-only view of the Docker test seed's blind votes, taken on its DAO parsing thread. */
public class RegtestSeedNode extends SeedNodeMain {
    public static void main(String[] args) {
        new RegtestSeedNode().execute(args);
    }

    @Override
    protected void startApplication() {
        super.startApplication();
        var dao = injector.getInstance(DaoStateService.class);
        var votes = injector.getInstance(BlindVoteListService.class);
        try {
            var server = HttpServer.create(new InetSocketAddress(5124), 0);
            server.createContext("/blind-votes", exchange -> {
                try (exchange) {
                    String counts = CompletableFuture.supplyAsync(() -> snapshot(dao, votes), UserThread::execute)
                            .completeOnTimeout(null, 1, TimeUnit.SECONDS).join();
                    if (counts == null) {
                        exchange.sendResponseHeaders(503, -1);
                        return;
                    }
                    byte[] body = counts.getBytes(StandardCharsets.US_ASCII);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the test seed's blind-vote probe", e);
        }
    }

    private static String snapshot(DaoStateService dao, BlindVoteListService votes) {
        var cycle = dao.getCurrentCycle();
        if (!dao.isParseBlockChainComplete() || cycle == null) return null;

        long expected = dao.getUnorderedTxStream()
                .filter(tx -> tx.getTxType() == TxType.BLIND_VOTE)
                .filter(tx -> cycle.isInPhase(tx.getBlockHeight(), DaoPhase.Phase.BLIND_VOTE))
                .count();
        int received = votes.getBlindVotesInPhaseAndCycle().size();
        return dao.getChainHeight() + " " + expected + " " + received;
    }
}
