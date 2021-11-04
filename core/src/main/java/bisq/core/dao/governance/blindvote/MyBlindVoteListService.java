/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.blindvote;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.exceptions.PublishToP2PNetworkException;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Merit;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.CryptoException;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;

import javax.crypto.SecretKey;

import java.io.IOException;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Publishes blind vote tx and blind vote payload to p2p network.
 * Maintains myBlindVoteList for own blind votes. Triggers republishing of my blind votes at startup during blind
 * vote phase of current cycle.
 * Publishes a BlindVote and the blind vote transaction.
 */
@Slf4j
public class MyBlindVoteListService implements PersistedDataHost, DaoStateListener, DaoSetupService {
    private final P2PService p2PService;
    private final DaoStateService daoStateService;
    private final PeriodService periodService;
    private final WalletsManager walletsManager;
    private final PersistenceManager<MyBlindVoteList> persistenceManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BallotListService ballotListService;
    private final MyVoteListService myVoteListService;
    private final MyProposalListService myProposalListService;
    private final ChangeListener<Number> numConnectedPeersListener;
    @Getter
    private final MyBlindVoteList myBlindVoteList = new MyBlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBlindVoteListService(P2PService p2PService,
                                  DaoStateService daoStateService,
                                  PeriodService periodService,
                                  WalletsManager walletsManager,
                                  PersistenceManager<MyBlindVoteList> persistenceManager,
                                  BsqWalletService bsqWalletService,
                                  BtcWalletService btcWalletService,
                                  BallotListService ballotListService,
                                  MyVoteListService myVoteListService,
                                  MyProposalListService myProposalListService) {
        this.p2PService = p2PService;
        this.daoStateService = daoStateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.persistenceManager = persistenceManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.ballotListService = ballotListService;
        this.myVoteListService = myVoteListService;
        this.myProposalListService = myProposalListService;

        this.persistenceManager.initialize(myBlindVoteList, PersistenceManager.Source.PRIVATE);

        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublishMyBlindVote();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        if (DevEnv.isDaoActivated()) {
            persistenceManager.readPersisted(persisted -> {
                        myBlindVoteList.setAll(persisted.getList());
                        completeHandler.run();
                    },
                    completeHandler);
        } else {
            completeHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        maybeRePublishMyBlindVote();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Tuple2<Coin, Integer> getMiningFeeAndTxVsize(Coin stake)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        // We set dummy opReturn data
        Coin blindVoteFee = BlindVoteConsensus.getFee(daoStateService, daoStateService.getChainHeight());
        Transaction dummyTx = getBlindVoteTx(stake, blindVoteFee, new byte[22]);
        Coin miningFee = dummyTx.getFee();
        int txVsize = dummyTx.getVsize();
        return new Tuple2<>(miningFee, txVsize);
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        try {
            SecretKey secretKey = BlindVoteConsensus.createSecretKey();
            BallotList sortedBallotList = BlindVoteConsensus.getSortedBallotList(ballotListService);
            byte[] encryptedVotes = getEncryptedVotes(sortedBallotList, secretKey);
            byte[] opReturnData = getOpReturnData(encryptedVotes);
            Coin blindVoteFee = BlindVoteConsensus.getFee(daoStateService, daoStateService.getChainHeight());
            Transaction blindVoteTx = getBlindVoteTx(stake, blindVoteFee, opReturnData);
            String blindVoteTxId = blindVoteTx.getTxId().toString();

            byte[] encryptedMeritList = getEncryptedMeritList(blindVoteTxId, secretKey);

            // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
            // blind vote stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
            // in worst case if it does not succeed the blind vote will be ignored anyway.
            // Inconsistently propagated blind votes in the p2p network could have potentially worse effects.
            BlindVote blindVote = new BlindVote(encryptedVotes,
                    blindVoteTxId,
                    stake.value,
                    encryptedMeritList,
                    new Date().getTime(),
                    new HashMap<>());
            addBlindVoteToList(blindVote);

            addToP2PNetwork(blindVote, errorMessage -> {
                log.error(errorMessage);
                exceptionHandler.handleException(new PublishToP2PNetworkException(errorMessage));
            });

            // We store our source data for the blind vote in myVoteList
            myVoteListService.createAndAddMyVote(sortedBallotList, secretKey, blindVote);

            publishTx(resultHandler, exceptionHandler, blindVoteTx);
        } catch (CryptoException | TransactionVerificationException | InsufficientMoneyException |
                WalletException | IOException exception) {
            log.error(exception.toString());
            exception.printStackTrace();
            exceptionHandler.handleException(exception);
        }
    }

    public long getCurrentlyAvailableMerit() {
        MeritList meritList = getMerits(null);
        return MeritConsensus.getCurrentlyAvailableMerit(meritList, daoStateService.getChainHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private byte[] getEncryptedVotes(BallotList sortedBallotList, SecretKey secretKey) throws CryptoException {
        // We don't want to store the proposal but only use the proposalTxId as reference in our encrypted list.
        // So we convert it to the VoteWithProposalTxIdList.
        // The VoteWithProposalTxIdList is used for serialisation with protobuffer, it is not actually persisted but we
        // use the PersistableList base class for convenience.
        final List<VoteWithProposalTxId> list = sortedBallotList.stream()
                .map(ballot -> new VoteWithProposalTxId(ballot.getTxId(), ballot.getVote()))
                .collect(Collectors.toList());
        final VoteWithProposalTxIdList voteWithProposalTxIdList = new VoteWithProposalTxIdList(list);
        log.info("voteWithProposalTxIdList used in blind vote. voteWithProposalTxIdList={}", voteWithProposalTxIdList);
        return BlindVoteConsensus.getEncryptedVotes(voteWithProposalTxIdList, secretKey);
    }

    private byte[] getOpReturnData(byte[] encryptedVotes) throws IOException {
        // We cannot use hash of whole blindVote data because we create the merit signature with the blindVoteTxId
        // So we use the encryptedVotes for the hash only.
        final byte[] hash = BlindVoteConsensus.getHashOfEncryptedVotes(encryptedVotes);
        log.info("Sha256Ripemd160 hash of encryptedVotes: " + Utilities.bytesAsHexString(hash));
        return BlindVoteConsensus.getOpReturnData(hash);
    }

    private byte[] getEncryptedMeritList(String blindVoteTxId, SecretKey secretKey) throws CryptoException {
        MeritList meritList = getMerits(blindVoteTxId);
        return BlindVoteConsensus.getEncryptedMeritList(meritList, secretKey);
    }

    // blindVoteTxId is null if we use the method from the getCurrentlyAvailableMerit call.
    public MeritList getMerits(@Nullable String blindVoteTxId) {
        // Create a lookup set for txIds of own comp. requests from past cycles (we ignore request form that cycle)
        Set<String> myCompensationProposalTxIs = myProposalListService.getList().stream()
                .filter(proposal -> proposal instanceof CompensationProposal)
                .map(Proposal::getTxId)
                .filter(txId -> periodService.isTxInPastCycle(txId, periodService.getChainHeight()))
                .collect(Collectors.toSet());

        return new MeritList(daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION).stream()
                .map(issuance -> {
                    checkArgument(issuance.getIssuanceType() == IssuanceType.COMPENSATION,
                            "IssuanceType must be COMPENSATION for MeritList");
                    // We check if it is our proposal
                    if (!myCompensationProposalTxIs.contains(issuance.getTxId()))
                        return null;

                    byte[] signatureAsBytes;
                    if (blindVoteTxId != null) {
                        String pubKey = issuance.getPubKey();
                        if (pubKey == null) {
                            // Maybe add exception
                            log.error("We did not have a pubKey in our issuance object. " +
                                    "txId={}, issuance={}", issuance.getTxId(), issuance);
                            return null;
                        }

                        DeterministicKey key = bsqWalletService.findKeyFromPubKey(Utilities.decodeFromHex(pubKey));
                        if (key == null) {
                            // Maybe add exception
                            log.error("We did not find the key for our compensation request. txId={}",
                                    issuance.getTxId());
                            return null;
                        }

                        // We sign the txId so we be sure that the signature could not be used by anyone else
                        // In the verification the txId will be checked as well.

                        // As we use BitcoinJ EC keys we extend our consensus dependency to BitcoinJ.
                        // Alternative would be to use our own Sig Key but then we need to share the key separately.
                        // The EC key is in the blockchain already. We prefer here to stick with EC key. If any change
                        // in BitcoinJ would break our consensus we would need to fall back to the old BitcoinJ EC
                        // implementation.
                        ECKey.ECDSASignature signature = bsqWalletService.isEncrypted() ?
                                key.sign(Sha256Hash.wrap(blindVoteTxId), bsqWalletService.getAesKey()) :
                                key.sign(Sha256Hash.wrap(blindVoteTxId));
                        signatureAsBytes = signature.toCanonicalised().encodeToDER();
                    } else {
                        // In case we use it for requesting the currently available merit we don't apply a signature
                        signatureAsBytes = new byte[0];
                    }
                    return new Merit(issuance, signatureAsBytes);

                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Merit::getIssuanceTxId))
                .collect(Collectors.toList()));
    }

    private void publishTx(ResultHandler resultHandler, ExceptionHandler exceptionHandler, Transaction blindVoteTx) {
        log.info("blindVoteTx={}", blindVoteTx.toString());
        walletsManager.publishAndCommitBsqTx(blindVoteTx, TxType.BLIND_VOTE, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("BlindVote tx published. txId={}", transaction.getTxId().toString());
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                exceptionHandler.handleException(exception);
            }
        });
    }

    private Transaction getBlindVoteTx(Coin stake, Coin fee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(fee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
    }

    private void maybeRePublishMyBlindVote() {
        // We do not republish during vote reveal phase as peer would reject blindVote data to protect against
        // late publishing attacks.
        // This attack is only relevant during the vote reveal phase as there it could cause damage by disturbing the
        // data view of the blind votes of the voter for creating the majority hash.
        // To republish after the vote reveal phase still makes sense to reduce risk that some nodes have not received
        // it and would need to request the data then in the vote result phase.
        if (!periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.VOTE_REVEAL)) {
            // We republish at each startup at any block during the cycle. We filter anyway for valid blind votes
            // of that cycle so it is 1 blind vote getting rebroadcast at each startup to my neighbors.
            // Republishing only will have effect if the payload creation date is < 5 hours as other nodes would not
            // accept payloads which are too old or are in future.
            // Only payloads received from seed nodes would ignore that date check.
            int minPeers = Config.baseCurrencyNetwork().isMainnet() ? 4 : 1;
            if ((p2PService.getNumConnectedPeers().get() >= minPeers && p2PService.isBootstrapped()) ||
                    Config.baseCurrencyNetwork().isRegtest()) {
                myBlindVoteList.stream()
                        .filter(blindVote -> periodService.isTxInPhaseAndCycle(blindVote.getTxId(),
                                DaoPhase.Phase.BLIND_VOTE,
                                periodService.getChainHeight()))
                        .forEach(blindVote -> addToP2PNetwork(blindVote, null));

                // We delay removal of listener as we call that inside listener itself.
                UserThread.execute(() -> p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener));
            }
        }
    }

    private void addToP2PNetwork(BlindVote blindVote, @Nullable ErrorMessageHandler errorMessageHandler) {
        BlindVotePayload blindVotePayload = new BlindVotePayload(blindVote);
        // We use reBroadcast flag here as we only broadcast our own blindVote and want to be sure it gets distributed
        // well.
        boolean success = p2PService.addPersistableNetworkPayload(blindVotePayload, true);

        if (success) {
            log.info("We added a blindVotePayload to the P2P network as append only data. blindVoteTxId={}",
                    blindVote.getTxId());
        } else {
            String msg = "Adding of blindVotePayload to P2P network failed. blindVoteTxId=" + blindVote.getTxId();
            log.error(msg);
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage(msg);
        }
    }

    private void addBlindVoteToList(BlindVote blindVote) {
        if (!myBlindVoteList.getList().contains(blindVote)) {
            myBlindVoteList.add(blindVote);
            requestPersistence();
        }
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
