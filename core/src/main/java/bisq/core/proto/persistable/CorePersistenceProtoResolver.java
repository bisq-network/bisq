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

package bisq.core.proto.persistable;

import bisq.core.account.sign.SignedWitnessStore;
import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.blindvote.MyBlindVoteList;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStore;
import bisq.core.dao.governance.bond.reputation.MyReputationList;
import bisq.core.dao.governance.myvote.MyVoteList;
import bisq.core.dao.governance.proofofburn.MyProofOfBurnList;
import bisq.core.dao.governance.proposal.MyProposalList;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStore;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStore;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.storage.BsqBlockStore;
import bisq.core.dao.state.storage.DaoStateStore;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputList;
import bisq.core.payment.PaymentAccountList;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.dispute.arbitration.ArbitrationDisputeList;
import bisq.core.support.dispute.mediation.MediationDisputeList;
import bisq.core.support.dispute.refund.RefundDisputeList;
import bisq.core.trade.model.TradableList;
import bisq.core.trade.statistics.TradeStatistics2Store;
import bisq.core.trade.statistics.TradeStatistics3Store;
import bisq.core.user.PreferencesPayload;
import bisq.core.user.UserPayload;

import bisq.network.p2p.mailbox.IgnoredMailboxMap;
import bisq.network.p2p.mailbox.MailboxMessageList;
import bisq.network.p2p.peers.peerexchange.PeerList;
import bisq.network.p2p.storage.persistence.RemovedPayloadsMap;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.NavigationPath;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Provider;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

// TODO Use ProtobufferException instead of ProtobufferRuntimeException
@Slf4j
@Singleton
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<BtcWalletService> btcWalletService;
    private final NetworkProtoResolver networkProtoResolver;

    @Inject
    public CorePersistenceProtoResolver(Provider<BtcWalletService> btcWalletService,
                                        NetworkProtoResolver networkProtoResolver) {
        this.btcWalletService = btcWalletService;
        this.networkProtoResolver = networkProtoResolver;
    }

    @Override
    public PersistableEnvelope fromProto(protobuf.PersistableEnvelope proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case SEQUENCE_NUMBER_MAP:
                    return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
                case PEER_LIST:
                    return PeerList.fromProto(proto.getPeerList());
                case ADDRESS_ENTRY_LIST:
                    return AddressEntryList.fromProto(proto.getAddressEntryList());
                case TRADABLE_LIST:
                    return TradableList.fromProto(proto.getTradableList(), this, btcWalletService.get());
                case ARBITRATION_DISPUTE_LIST:
                    return ArbitrationDisputeList.fromProto(proto.getArbitrationDisputeList(), this);
                case MEDIATION_DISPUTE_LIST:
                    return MediationDisputeList.fromProto(proto.getMediationDisputeList(), this);
                case REFUND_DISPUTE_LIST:
                    return RefundDisputeList.fromProto(proto.getRefundDisputeList(), this);
                case PREFERENCES_PAYLOAD:
                    return PreferencesPayload.fromProto(proto.getPreferencesPayload(), this);
                case USER_PAYLOAD:
                    return UserPayload.fromProto(proto.getUserPayload(), this);
                case NAVIGATION_PATH:
                    return NavigationPath.fromProto(proto.getNavigationPath());
                case PAYMENT_ACCOUNT_LIST:
                    return PaymentAccountList.fromProto(proto.getPaymentAccountList(), this);
                case ACCOUNT_AGE_WITNESS_STORE:
                    return AccountAgeWitnessStore.fromProto(proto.getAccountAgeWitnessStore());
                case TRADE_STATISTICS2_STORE:
                    return TradeStatistics2Store.fromProto(proto.getTradeStatistics2Store());
                case BLIND_VOTE_STORE:
                    return BlindVoteStore.fromProto(proto.getBlindVoteStore());
                case PROPOSAL_STORE:
                    return ProposalStore.fromProto(proto.getProposalStore());
                case TEMP_PROPOSAL_STORE:
                    return TempProposalStore.fromProto(proto.getTempProposalStore(), networkProtoResolver);
                case MY_PROPOSAL_LIST:
                    return MyProposalList.fromProto(proto.getMyProposalList());
                case BALLOT_LIST:
                    return BallotList.fromProto(proto.getBallotList());
                case MY_VOTE_LIST:
                    return MyVoteList.fromProto(proto.getMyVoteList());
                case MY_BLIND_VOTE_LIST:
                    return MyBlindVoteList.fromProto(proto.getMyBlindVoteList());
                case DAO_STATE_STORE:
                    return DaoStateStore.fromProto(proto.getDaoStateStore());
                case MY_REPUTATION_LIST:
                    return MyReputationList.fromProto(proto.getMyReputationList());
                case MY_PROOF_OF_BURN_LIST:
                    return MyProofOfBurnList.fromProto(proto.getMyProofOfBurnList());
                case UNCONFIRMED_BSQ_CHANGE_OUTPUT_LIST:
                    return UnconfirmedBsqChangeOutputList.fromProto(proto.getUnconfirmedBsqChangeOutputList());
                case SIGNED_WITNESS_STORE:
                    return SignedWitnessStore.fromProto(proto.getSignedWitnessStore());
                case TRADE_STATISTICS3_STORE:
                    return TradeStatistics3Store.fromProto(proto.getTradeStatistics3Store());
                case MAILBOX_MESSAGE_LIST:
                    return MailboxMessageList.fromProto(proto.getMailboxMessageList(), networkProtoResolver);
                case IGNORED_MAILBOX_MAP:
                    return IgnoredMailboxMap.fromProto(proto.getIgnoredMailboxMap());
                case REMOVED_PAYLOADS_MAP:
                    return RemovedPayloadsMap.fromProto(proto.getRemovedPayloadsMap());
                case BSQ_BLOCK_STORE:
                    return BsqBlockStore.fromProto(proto.getBsqBlockStore());
                default:
                    throw new ProtobufferRuntimeException("Unknown proto message case(PB.PersistableEnvelope). " +
                            "messageCase=" + proto.getMessageCase() + "; proto raw data=" + proto.toString());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PersistableEnvelope is null");
            throw new ProtobufferRuntimeException("PB.PersistableEnvelope is null");
        }
    }
}
