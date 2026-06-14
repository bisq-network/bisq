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
import bisq.proto.grpc.OfferInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
        injectRawTx(owner, txId);
        generateBlocks(1);
    }

    /** Push a Bisq-broadcast tx into bitcoind's mempool WITHOUT mining (see {@link #confirmTx}
     *  for the inject-vs-P2P-relay rationale). Lets a caller stage several txs and then
     *  confirm them all in a single block. Dedupes if bitcoinj's relay already landed it. */
    private void injectRawTx(GrpcClient owner, String txId) {
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
    }

    /** Convenience: confirm a tx owned by {@code alice}. */
    public void confirmTx(String txId) {
        confirmTx(alice, txId);
    }

    /**
     * Confirm a tx whose confirmation the {@code owner}'s own SPV (bitcoinj) wallet must
     * observe — i.e. where {@code owner.getTrade(..).getIsDepositConfirmed()} (or the payout
     * equivalent) is the gate. Unlike {@link #confirmTx}, this first waits until bitcoind's
     * mempool holds the tx via the owner's bitcoinj P2P broadcast, and only then mines.
     *
     * <p>Why this matters: bitcoind serves the SPV wallet bloom-filtered {@code merkleblock}s.
     * When the owner builds this tx, bitcoinj adds its scripts/outpoints to the wallet and
     * sends an updated {@code filterload} to bitcoind, immediately followed by the tx
     * broadcast — both on the SAME TCP connection. If we mine the confirming block before
     * that {@code filterload} reaches bitcoind (a race the rapid back-to-back soak loses
     * around trade ~34), the {@code merkleblock} for that block omits this tx and the owner's
     * wallet connects the block WITHOUT ever marking the tx confirmed — it stays "unconfirmed"
     * in the wallet for minutes (until bitcoinj's next periodic filter resend) even though it
     * is buried on-chain. Because filterload precedes the tx on the same ordered connection,
     * observing the tx in bitcoind's mempool (from the owner's broadcast) proves bitcoind
     * already holds the updated filter; mining after that yields a matching merkleblock.
     *
     * <p>If the broadcast has not landed within {@code BROADCAST_GRACE_MS} (bitcoinj's P2P
     * broadcast can itself stall against bitcoind v29 — the reason {@link #confirmTx} injects),
     * fall back to direct injection and mine anyway; a rare filter-race miss self-heals on the
     * next wallet activity and is covered by the caller's generous confirmation timeout.
     */
    public void confirmTxAfterFilterPropagation(GrpcClient owner, String txId) {
        if (!awaitTxInMempool(txId, BROADCAST_GRACE_MS)) {
            injectRawTx(owner, txId);
        }
        generateBlocks(1);
    }

    private static final long BROADCAST_GRACE_MS = 15_000;

    /** Poll bitcoind until {@code txId} is in its mempool, up to {@code graceMs}. */
    private boolean awaitTxInMempool(String txId, long graceMs) {
        long deadline = System.currentTimeMillis() + graceMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                runBitcoinCli("getmempoolentry", txId); // exits non-zero if absent
                return true;
            } catch (RuntimeException notYet) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
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

    /**
     * Confirm every owner's auto-broadcast vote-reveal tx together in a SINGLE block.
     *
     * <p>A reveal tx only counts toward the result if it is confirmed inside the
     * VOTE_REVEAL phase — just 2 blocks on regtest ({@code Param.PHASE_VOTE_REVEAL}).
     * Confirming one owner per call (repeated {@link #confirmAutoRevealsFor}) mines one
     * block each, so a second voter's reveal lands in the block that crosses out of the
     * phase and its vote is silently dropped from the tally. Staging all reveals into the
     * mempool and mining once keeps them in the same in-phase block.
     *
     * <p>Call right after entering VOTE_REVEAL ({@code advanceToPhase(DAO_PHASE_VOTE_REVEAL)}
     * lands on the phase's first block): the single block this mines is then the phase's
     * second/last block, still in-phase.
     */
    public void confirmAutoRevealsForAll(GrpcClient... owners) {
        java.util.List<String> injected = new java.util.ArrayList<>();
        for (GrpcClient owner : owners) {
            await(() -> owner.getMyVotes().getVotesList().stream()
                            .anyMatch(v -> !v.getRevealTxId().isEmpty()),
                    10_000, "auto-reveal tx broadcast");
            for (bisq.proto.grpc.MyVoteInfo v : owner.getMyVotes().getVotesList()) {
                String revealTxId = v.getRevealTxId();
                if (!revealTxId.isEmpty() && !injected.contains(revealTxId)) {
                    injectRawTx(owner, revealTxId);
                    injected.add(revealTxId);
                }
            }
        }
        generateBlocks(1);
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

    /**
     * Place a v1 offer, retrying while {@code ValidateOffer} rejects it because the maker
     * has not yet observed an available mediator + refund agent.
     *
     * <p>Those agents are registered on the arb node at stack startup and reach the maker
     * via P2P gossip. On a freshly reset stack the maker's very first {@code createoffer}
     * can run before that payload arrives, so {@code ValidateOffer.checkDisputeAgentAvailability}
     * fails ("no accepted mediator" / "no refund agent") and the call throws. {@code ValidateOffer}
     * is the first task in the place-offer protocol — it runs before the maker-fee tx is
     * built and before any funds are reserved — so a rejected attempt leaves no offer and no
     * wallet state, making retries side-effect free.
     *
     * <p>Only the dispute-agent race is retried: every other {@code ValidateOffer} check is
     * deterministic in the request, so a genuinely bad offer fails identically on each attempt
     * and surfaces at the timeout. Gate on the placement succeeding; the timeout is the safety net.
     */
    public static OfferInfo placeV1OfferWhenReady(Supplier<OfferInfo> placeOffer) {
        long deadline = System.currentTimeMillis() + 60_000;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return placeOffer.get();
            } catch (RuntimeException ex) {
                if (!isMakerNotYetReadyForOffer(ex)) throw ex;
                last = ex;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new AssertionError("offer placement never accepted within 60s — maker may never "
                + "have received the mediator/refund-agent payloads", last);
    }

    /**
     * True if {@code ex} is a place-offer {@code ValidateOffer} failure.
     *
     * <p>The daemon collapses every ValidateOffer failure to a single lowercased line
     * ("...at task: validateoffer") before it reaches the gRPC client — {@code Task.failed}
     * does not append the cause and {@code GrpcExceptionHandler} keeps only the last line —
     * so the specific reason ("no accepted mediator", etc.) is never transmitted and cannot
     * be matched. The dispute-agent race is the only non-deterministic ValidateOffer
     * rejection for a well-formed offer; a genuinely invalid offer fails identically on
     * every retry and surfaces when {@link #placeV1OfferWhenReady} times out.
     */
    private static boolean isMakerNotYetReadyForOffer(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("validateoffer");
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
