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

package bisq.apitest.dao;

import bisq.cli.GrpcClient;

import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.GetCycleInfoReply;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Helpers shared across DAO tests: phase advancement, polling, container exec.
 * State-free; only the bitcoind container name is held as a convenience.
 */
@Slf4j
public class DaoTestUtils {

    private final GrpcClient alice;
    private final GrpcClient bob;
    private final String bitcoindContainer;
    private final String minerAddress;

    public DaoTestUtils(GrpcClient alice, GrpcClient bob, String bitcoindContainer) {
        this.alice = alice;
        this.bob = bob;
        this.bitcoindContainer = bitcoindContainer;
        this.minerAddress = fetchMinerAddress();
    }

    public GrpcClient alice() { return alice; }
    public GrpcClient bob()   { return bob; }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Phase advancement via bitcoin-cli generatetoaddress
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Mine N blocks and wait until alice and bob both observe the new chain tip.
     * The target height is read from bitcoind directly — reading {@code alice.getCycleInfo()}
     * here races the daemon's block-notification handler and would let
     * {@link #awaitChainHeight} return before Bisq actually finished parsing the new blocks.
     */
    public void generateBlocks(int n) {
        if (n <= 0) return;
        runBitcoinCli("generatetoaddress", String.valueOf(n), minerAddress);
        int target = Integer.parseInt(runBitcoinCli("getblockcount").trim());
        awaitChainHeight(alice, target, "alice");
        awaitChainHeight(bob, target, "bob");
    }

    /**
     * Walk forward until both nodes report being inside the target phase. If the target phase
     * lies in the past relative to current cycle, advance into the next cycle.
     */
    /**
     * Advance until {@code target} phase is entered. If we are already inside the target
     * phase, return immediately. The follow-up callers ({@link #confirmTx}) then mine
     * blocks; the entire sequence is deterministic given regtest's fixed phase durations.
     *
     * <p>If a test needs at least {@code minBlocksRemaining} blocks of headroom inside
     * {@code target} (e.g. to confirm multiple BSQ txs without crossing into the next
     * phase), use {@link #advanceToPhase(DaoPhaseEnum, int)} below.
     */
    public void advanceToPhase(DaoPhaseEnum target) {
        advanceToPhase(target, 1);
    }

    /**
     * Advance until {@code target} phase is entered with at least {@code minBlocksRemaining}
     * blocks left in that phase. If the current position in {@code target} is too late,
     * mine through to the next cycle's occurrence of {@code target}.
     */
    public void advanceToPhase(DaoPhaseEnum target, int minBlocksRemaining) {
        for (int safety = 0; safety < 60; safety++) {
            GetCycleInfoReply ci = alice.getCycleInfo();
            if (ci.getPhase() == target && ci.getBlocksRemainingInPhase() >= minBlocksRemaining) {
                awaitPhaseOnPeer(bob, target);
                return;
            }
            int blocksToMine = Math.max(1, ci.getBlocksRemainingInPhase() + 1);
            generateBlocks(blocksToMine);
        }
        throw new IllegalStateException("could not reach phase " + target
                + " after 60 hops; last seen=" + alice.getCycleInfo().getPhase());
    }

    public void advanceFullCycle() {
        int startIdx = alice.getCycleInfo().getCycleIndex();
        for (int safety = 0; safety < 20; safety++) {
            int idx = alice.getCycleInfo().getCycleIndex();
            if (idx > startIdx) return;
            int remaining = Math.max(1, alice.getCycleInfo().getBlocksRemainingInPhase() + 1);
            generateBlocks(remaining);
        }
        throw new IllegalStateException("cycle did not advance after 20 hops");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Polling
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void awaitChainHeight(GrpcClient client, int targetHeight, String label) {
        await(() -> client.getCycleInfo().getChainHeight() >= targetHeight,
                30_000, "chain height >= " + targetHeight + " on " + label);
    }

    /**
     * Phase derives from chain height + parsed DAO state. {@link #generateBlocks(int)}
     * already synchronizes both peers' chain heights, but the peer's DAO state parser
     * (block-notify → JSON-RPC → parse → state update) runs slightly after the chain
     * height update. Spin briefly on the chain-tip parse to settle, then verify phase.
     * Deterministic in steady state: bounded by single-block parse time, not by P2P relay.
     */
    public void awaitPhaseOnPeer(GrpcClient client, DaoPhaseEnum target) {
        await(() -> client.getCycleInfo().getPhase() == target,
                10_000, "peer phase " + target);
    }

    /**
     * Confirm a Bisq-broadcast BSQ tx deterministically:
     * <ol>
     *   <li>Fetch the serialized tx hex from the originating Bisq daemon (the wallet
     *       commits the tx synchronously when publish returns, so it is always there).</li>
     *   <li>Inject directly via bitcoind's {@code sendrawtransaction} RPC. This bypasses
     *       bitcoinj's INV/getdata P2P relay (which has been observed to stall for tens
     *       of seconds against bitcoind v29). bitcoind dedupes if the tx is already in
     *       the mempool, so an injection that races with the P2P arrival is a no-op.</li>
     *   <li>Mine one block — the tx is in the mempool, so it is included.</li>
     * </ol>
     */
    public void confirmTx(GrpcClient owner, String txId) {
        String hex = owner.getRawTransaction(txId);
        if (hex == null || hex.isEmpty()) {
            throw new AssertionError("daemon does not know tx " + txId
                    + " — was the tx broadcast by this client?");
        }
        try {
            runBitcoinCli("sendrawtransaction", hex);
        } catch (RuntimeException ex) {
            // Acceptable: bitcoinj's own broadcast may have already landed the tx and
            // bitcoind responds with "transaction already in block chain" or the dedupe
            // error. Any other failure is propagated by the subsequent generateBlocks.
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            boolean dedupe = msg.contains("already in") || msg.contains("txn-already")
                    || msg.contains("inserttx");
            if (!dedupe) throw ex;
        }
        generateBlocks(1);
    }

    /** Convenience: confirm a tx owned by {@code alice}. */
    public void confirmTx(String txId) {
        confirmTx(alice, txId);
    }

    /**
     * Bisq's VoteRevealService auto-broadcasts a reveal tx for every unrevealed
     * {@link bisq.proto.grpc.MyVoteInfo} when the chain enters VOTE_REVEAL phase.
     * The broadcast is fire-and-forget over bitcoinj P2P; for deterministic
     * confirmation tests must inject the resulting raw tx and mine. This helper
     * picks up any reveal tx the daemon has produced and confirms it.
     *
     * @return list of confirmed reveal tx ids
     */
    /**
     * Block until {@code client} sees at least {@code expected} blind-vote payloads in
     * the current cycle. Blind-vote payloads propagate via Bisq's P2P storage between
     * Bisq peers (not via bitcoind), so a tx-only injection does not by itself give a
     * peer the encrypted vote payload it needs to decrypt at result time. Use this to
     * gate progression past BLIND_VOTE until the payloads have replicated.
     */
    public void awaitBlindVotePropagation(GrpcClient client, int expected, String label) {
        await(() -> client.getCycleInfo().getNumBlindVotesInCurrentCycle() >= expected,
                20_000, label + " sees >=" + expected + " blind-vote payloads in current cycle");
    }

    public java.util.List<String> confirmAutoRevealsFor(GrpcClient owner) {
        // Wait briefly for VoteRevealService.UserThread.execute fire after chain tip
        // hits VOTE_REVEAL — bounded by a single executor tick (~ms).
        await(() -> owner.getMyVotes().getVotesList().stream()
                        .anyMatch(v -> !v.getRevealTxId().isEmpty()),
                10_000, "auto-reveal tx broadcast");
        java.util.List<String> revealTxIds = new java.util.ArrayList<>();
        for (bisq.proto.grpc.MyVoteInfo v : owner.getMyVotes().getVotesList()) {
            if (!v.getRevealTxId().isEmpty() && !revealTxIds.contains(v.getRevealTxId())) {
                revealTxIds.add(v.getRevealTxId());
            }
        }
        for (String revealTxId : revealTxIds) {
            confirmTx(owner, revealTxId);
        }
        return revealTxIds;
    }

    public void awaitDaoStateReady(GrpcClient client, String label) {
        // isDaoStateInSync also checks consensus with seed node — but seed and daemon
        // can legitimately disagree on the *proposal payload set* (P2P gossip races)
        // even when they agree on the chain itself. For test readiness, only require
        // chain parsing to have produced a current cycle (chainHeight > 0).
        await(() -> {
            try {
                return client.getCycleInfo().getChainHeight() > 0
                        && client.getCycleInfo().getCycleIndex() >= 0;
            } catch (RuntimeException ex) {
                return false;
            }
        }, 60_000, "DAO state ready on " + label);
    }

    public static void await(BooleanSupplier cond, long timeoutMillis, String what) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (cond.getAsBoolean()) return;
            } catch (RuntimeException ex) {
                // transient — keep polling
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bitcoin-cli exec
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String fetchMinerAddress() {
        // bitcoind v29 dropped legacy wallet support. bitcoind-entrypoint.sh creates a
        // fresh descriptor wallet "testwallet" at container start; getnewaddress from it
        // returns a valid bech32 regtest address we can mine to. Retry briefly in case
        // the wallet creation hasn't completed when the first test runs.
        IllegalStateException last = null;
        for (int i = 0; i < 30; i++) {
            try {
                return runBitcoinCli("getnewaddress").trim();
            } catch (IllegalStateException ex) {
                last = ex;
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new IllegalStateException("bitcoind getnewaddress never succeeded — is testwallet loaded?", last);
    }

    public String runBitcoinCli(String... args) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add(bitcoindContainer);
        cmd.add("bitcoin-cli");
        cmd.add("-regtest");
        cmd.add("-rpcuser=bisqdao");
        cmd.add("-rpcpassword=bsq");
        for (String a : args) cmd.add(a);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.lines().collect(Collectors.joining("\n"));
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("bitcoin-cli timed out: " + String.join(" ", cmd));
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("bitcoin-cli failed (" + p.exitValue() + "): "
                        + String.join(" ", cmd) + "\n" + out);
            }
            return out;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("bitcoin-cli invocation failed", ex);
        }
    }
}
