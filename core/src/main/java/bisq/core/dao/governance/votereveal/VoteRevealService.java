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
import bisq.core.btc.exceptions.TxMalleabilityException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.BlindVoteValidator;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import bisq.network.p2p.P2PService;

import bisq.common.util.Utilities;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO case that user misses reveal phase not impl. yet


// TODO We could also broadcast the winning list at the moment the reveal period is over and have the break
// interval as time buffer for all nodes to receive that winning list. All nodes which are in sync with the
// majority data view can broadcast. That way it will become a very unlikely case that a node is missing
// data.

/**
 * Publishes voteRevealTx with the secret key used for encryption at blind vote and the hash of the list of
 * the blind vote payloads. Republishes also all blindVotes of that cycle to add more resilience.
 */
@Slf4j
public class VoteRevealService implements BsqStateListener, DaoSetupService {
    private final BsqStateService bsqStateService;
    private final BlindVoteListService blindVoteListService;
    private final BlindVoteValidator blindVoteValidator;
    private final PeriodService periodService;
    private final MyVoteListService myVoteListService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;

    //TODO UI should listen to that
    @Getter
    private final ObservableList<VoteRevealException> voteRevealExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteRevealService(BsqStateService bsqStateService,
                             BlindVoteListService blindVoteListService,
                             BlindVoteValidator blindVoteValidator,
                             PeriodService periodService,
                             MyVoteListService myVoteListService,
                             BsqWalletService bsqWalletService,
                             BtcWalletService btcWalletService,
                             P2PService p2PService,
                             WalletsManager walletsManager) {
        this.bsqStateService = bsqStateService;
        this.blindVoteListService = blindVoteListService;
        this.blindVoteValidator = blindVoteValidator;
        this.periodService = periodService;
        this.myVoteListService = myVoteListService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.p2PService = p2PService;
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
        bsqStateService.addBsqStateListener(this);
    }

    @Override
    public void start() {
        maybeRevealVotes(bsqStateService.getChainHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public byte[] getHashOfBlindVoteList() {
        List<BlindVote> blindVotes = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
        return VoteRevealConsensus.getHashOfBlindVoteList(blindVotes);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        // TODO check if we should use onParseTxsComplete for calling maybeCalculateVoteResult
        maybeRevealVotes(blockHeight);
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of vote reveal tx is done without user activity!
    // We create automatically the vote reveal tx when we enter the reveal phase of the current cycle when
    // the blind vote was created in case we have not done it already.
    // The voter need to be at least once online in the reveal phase when he has a blind vote created,
    // otherwise his vote becomes invalid and his locked stake will get unlocked
    private void maybeRevealVotes(int chainHeight) {
        if (periodService.getPhaseForHeight(chainHeight) == DaoPhase.Phase.VOTE_REVEAL) {
            myVoteListService.getMyVoteList().stream()
                    .filter(myVote -> myVote.getRevealTxId() == null) // we have not already revealed TODO
                    .filter(myVote -> periodService.isTxInCorrectCycle(myVote.getTxId(), chainHeight))
                    .forEach(myVote -> {
                        // We handle the exception here inside the stream iteration as we have not get triggered from an
                        // outside user intent anyway. We keep errors in a observable list so clients can observe that to
                        // get notified if anything went wrong.
                        try {
                            revealVote(myVote, chainHeight);
                        } catch (IOException | WalletException | TransactionVerificationException
                                | InsufficientMoneyException e) {
                            voteRevealExceptions.add(new VoteRevealException("Exception at calling revealVote.",
                                    e, myVote.getTxId()));
                        } catch (VoteRevealException e) {
                            voteRevealExceptions.add(e);
                        }
                    });
        }
    }

    private void revealVote(MyVote myVote, int chainHeight) throws IOException, WalletException,
            InsufficientMoneyException, TransactionVerificationException, VoteRevealException {
        // We collect all valid blind vote items we received via the p2p network.
        // It might be that different nodes have a different collection of those items.
        // To ensure we get a consensus of the data for later calculating the result we will put a hash of each
        // voters  blind vote collection into the opReturn data and check for a majority at issuance time.
        // The voters "vote" with their stake at the reveal tx for their version of the blind vote collection.

        // TODO make more clear by using param like here:
       /* List<BlindVote> blindVotes = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
         VoteRevealConsensus.getHashOfBlindVoteList(blindVotes);*/

        byte[] hashOfBlindVoteList = getHashOfBlindVoteList();

        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        byte[] opReturnData = VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList, myVote.getSecretKey());

        // We search for my unspent stake output.
        // myVote is already tested if it is in current cycle at maybeRevealVotes
        // We expect that the blind vote tx and stake output is available. If not we throw an exception.
        TxOutput stakeTxOutput = bsqStateService.getUnspentBlindVoteStakeTxOutputs().stream()
                .filter(txOutput -> txOutput.getTxId().equals(myVote.getTxId()))
                .findFirst()
                .orElseThrow(() -> new VoteRevealException("stakeTxOutput is not found for myVote.", myVote));

        // TxOutput has to be in the current cycle. Phase is checked in the parser anyway.
        // TODO is phase check needed and done in parser still?
        if (periodService.isTxInCorrectCycle(stakeTxOutput.getTxId(), chainHeight)) {
            Transaction voteRevealTx = getVoteRevealTx(stakeTxOutput, opReturnData);
            log.info("voteRevealTx={}", voteRevealTx);
            publishTx(voteRevealTx);

            // TODO add comment...
            // We don't want to wait for a successful broadcast to avoid issues if the broadcast succeeds delayed or at
            // next startup but the tx was actually broadcasted.
            myVoteListService.applyRevealTxId(myVote, voteRevealTx.getHashAsString());

            // Just for additional resilience we republish our blind votes
            final List<BlindVote> sortedBlindVoteListOfCycle = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
            rePublishBlindVotePayloadList(sortedBlindVoteListOfCycle);
        } else {
            final String msg = "Tx of stake out put is not in our cycle. That must not happen.";
            log.error("{}. chainHeight={},  blindVoteTxId()={}", msg, chainHeight, myVote.getTxId());
            voteRevealExceptions.add(new VoteRevealException(msg,
                    stakeTxOutput.getTxId()));
        }
    }

    private void publishTx(Transaction voteRevealTx) {
        walletsManager.publishAndCommitBsqTx(voteRevealTx, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("voteRevealTx successfully broadcasted.");
            }

            @Override
            public void onTxMalleability(TxMalleabilityException exception) {
                log.error(exception.toString());
                // TODO handle
                voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                        exception, voteRevealTx));
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                log.error(exception.toString());
                // TODO handle
                voteRevealExceptions.add(new VoteRevealException("Publishing of voteRevealTx failed.",
                        exception, voteRevealTx));
            }
        });
    }

    private Transaction getVoteRevealTx(TxOutput stakeTxOutput, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedVoteRevealTx(stakeTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedVoteRevealTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    private void rePublishBlindVotePayloadList(List<BlindVote> blindVoteList) {
        blindVoteList.stream()
                .map(BlindVotePayload::new)
                .forEach(blindVotePayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(blindVotePayload, true);
                    if (!success)
                        log.warn("publishToAppendOnlyDataStore failed for blindVote " + blindVotePayload.getBlindVote());
                });
    }
}
