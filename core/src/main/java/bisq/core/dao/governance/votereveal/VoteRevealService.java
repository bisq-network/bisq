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

package bisq.core.dao.governance.votereveal;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.common.util.Utilities;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// TODO Broadcast the winning list at the moment the reveal period is over and have the break
// interval as time buffer for all nodes to receive that winning list. All nodes which are in sync with the
// majority data view can broadcast. That way it will become a very unlikely case that a node is missing
// data.

/**
 * Publishes voteRevealTx with the secret key used for encryption at blind vote and the hash of the list of
 * the blind vote payloads. Republishes also all blindVotes of that cycle to add more resilience.
 */
@Slf4j
public class VoteRevealService implements DaoStateListener, DaoSetupService {

    public interface VoteRevealTxPublishedListener {
        void onVoteRevealTxPublished(String txId);
    }

    private final DaoStateService daoStateService;
    private final BlindVoteListService blindVoteListService;
    private final PeriodService periodService;
    private final MyVoteListService myVoteListService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;

    @Getter
    private final ObservableList<VoteRevealException> voteRevealExceptions = FXCollections.observableArrayList();
    private final List<VoteRevealTxPublishedListener> voteRevealTxPublishedListeners = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteRevealService(DaoStateService daoStateService,
                             BlindVoteListService blindVoteListService,
                             PeriodService periodService,
                             MyVoteListService myVoteListService,
                             BsqWalletService bsqWalletService,
                             BtcWalletService btcWalletService,
                             WalletsManager walletsManager) {
        this.daoStateService = daoStateService;
        this.blindVoteListService = blindVoteListService;
        this.periodService = periodService;
        this.myVoteListService = myVoteListService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        voteRevealExceptions.addListener((ListChangeListener<VoteRevealException>) c -> {
            c.next();
            if (c.wasAdded())
                c.getAddedSubList().forEach(exception -> log.error(exception.toString()));
        });
        daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private byte[] getHashOfBlindVoteList() {
        List<BlindVote> blindVotes = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVotes);
        log.debug("blindVoteList for creating hash: {}", blindVotes);
        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList {}", Utilities.bytesAsHexString(hashOfBlindVoteList));
        return hashOfBlindVoteList;
    }

    public void addVoteRevealTxPublishedListener(VoteRevealTxPublishedListener voteRevealTxPublishedListener) {
        voteRevealTxPublishedListeners.add(voteRevealTxPublishedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        maybeRevealVotes(block.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of vote reveal tx is done without user activity!
    // We create automatically the vote reveal tx when we are in the reveal phase of the current cycle when
    // the blind vote was created in case we have not done it already.
    // The voter needs to be at least once online in the reveal phase when he has a blind vote created,
    // otherwise his vote becomes invalid.
    // In case the user misses the vote reveal phase an (invalid) vote reveal tx will be created the next time the user is
    // online. That tx only serves the purpose to unlock the stake from the blind vote but it will be ignored for voting.
    // A blind vote which did not get revealed might still be part of the majority hash calculation as we cannot know
    // which blind votes might be revealed until the phase is over at the moment when we publish the vote reveal tx.
    private void maybeRevealVotes(int chainHeight) {
        myVoteListService.getMyVoteList().stream()
                .filter(myVote -> myVote.getRevealTxId() == null) // we have not already revealed
                .forEach(myVote -> {
                    boolean isInVoteRevealPhase = periodService.getPhaseForHeight(chainHeight) == DaoPhase.Phase.VOTE_REVEAL;
                    // If we would create the tx in the last block it would be confirmed in the best case in th next
                    // block which would be already the break and would invalidate the vote reveal.
                    boolean isLastBlockInPhase = chainHeight == periodService.getLastBlockOfPhase(chainHeight, DaoPhase.Phase.VOTE_REVEAL);
                    String blindVoteTxId = myVote.getBlindVoteTxId();
                    boolean isBlindVoteTxInCorrectPhaseAndCycle = periodService.isTxInPhaseAndCycle(blindVoteTxId, DaoPhase.Phase.BLIND_VOTE, chainHeight);
                    if (isInVoteRevealPhase && !isLastBlockInPhase && isBlindVoteTxInCorrectPhaseAndCycle) {
                        log.info("We call revealVote at blockHeight {} for blindVoteTxId {}", chainHeight, blindVoteTxId);
                        // Standard case that we are in the correct phase and cycle and create the reveal tx.
                        revealVote(myVote, true);
                    } else {
                        // We missed the vote reveal phase but publish a vote reveal tx to unlock the blind vote stake.
                        boolean isAfterVoteRevealPhase = periodService.getPhaseForHeight(chainHeight).ordinal() > DaoPhase.Phase.VOTE_REVEAL.ordinal();

                        // We missed the reveal phase but we are in the correct cycle
                        boolean missedPhaseSameCycle = isAfterVoteRevealPhase && isBlindVoteTxInCorrectPhaseAndCycle;

                        // If we missed the cycle we don't care about the phase anymore.
                        boolean isBlindVoteTxInPastCycle = periodService.isTxInPastCycle(blindVoteTxId, chainHeight);

                        if (missedPhaseSameCycle || isBlindVoteTxInPastCycle) {
                            // Exceptional case that the user missed the vote reveal phase. We still publish the vote
                            // reveal tx to unlock the vote stake.

                            // We cannot handle that case in the parser directly to avoid that reveal tx and unlock the
                            // BSQ because the blind vote tx is already in the snapshot and does not get parsed
                            // again. It would require a reset of the snapshot and parse all blocks again.
                            // As this is an exceptional case we prefer to have a simple solution instead and just
                            // publish the vote reveal tx but are aware that it is invalid.
                            log.warn("We missed the vote reveal phase but publish now the tx to unlock our locked " +
                                            "BSQ from the blind vote tx. BlindVoteTxId={}, blockHeight={}",
                                    blindVoteTxId, chainHeight);

                            // We handle the exception here inside the stream iteration as we have not get triggered from an
                            // outside user intent anyway. We keep errors in a observable list so clients can observe that to
                            // get notified if anything went wrong.
                            revealVote(myVote, false);
                        }
                    }
                });
    }

    private void revealVote(MyVote myVote, boolean isInVoteRevealPhase) {
        try {
            // We collect all valid blind vote items we received via the p2p network.
            // It might be that different nodes have a different collection of those items.
            // To ensure we get a consensus of the data for later calculating the result we will put a hash of each
            // voter's blind vote collection into the opReturn data and check for a majority in the vote result phase.
            // The voters "vote" with their stake at the reveal tx for their version of the blind vote collection.

            // If we are not in the right phase we just add an empty hash (still need to have the hash as otherwise we
            // would not recognize the tx as vote reveal tx)
            byte[] hashOfBlindVoteList = isInVoteRevealPhase ? getHashOfBlindVoteList() : new byte[20];
            byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

            // We search for my unspent stake output.
            // myVote is already tested if it is in current cycle at maybeRevealVotes
            // We expect that the blind vote tx and stake output is available. If not we throw an exception.
            TxOutput stakeTxOutput = daoStateService.getUnspentBlindVoteStakeTxOutputs().stream()
                    .filter(txOutput -> txOutput.getTxId().equals(myVote.getBlindVoteTxId()))
                    .findFirst()
                    .orElseThrow(() -> new VoteRevealException("stakeTxOutput is not found for myVote.", myVote));

            Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);
            log.info("voteRevealTx={}", voteRevealTx);
            publishTx(voteRevealTx);

            // We don't want to wait for a successful broadcast to avoid issues if the broadcast succeeds delayed or at
            // next startup but the tx was actually broadcast.
            myVoteListService.applyRevealTxId(myVote, voteRevealTx.getTxId().toString());
        } catch (IOException | WalletException | TransactionVerificationException
                | InsufficientMoneyException e) {
            voteRevealExceptions.add(new VoteRevealException("Exception at calling revealVote.",
                    e, myVote.getBlindVoteTxId()));
        } catch (VoteRevealException e) {
            voteRevealExceptions.add(e);
        }
    }

    private void publishTx(Transaction voteRevealTx) {
        walletsManager.publishAndCommitBsqTx(voteRevealTx, TxType.VOTE_REVEAL, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("voteRevealTx successfully broadcast.");
                voteRevealTxPublishedListeners.forEach(l -> l.onVoteRevealTxPublished(transaction.getTxId().toString()));
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                log.error(exception.toString());
                voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                        exception, voteRevealTx));
            }
        });
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
    }
}
