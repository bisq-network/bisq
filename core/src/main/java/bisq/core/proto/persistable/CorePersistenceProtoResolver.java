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

import bisq.core.arbitration.DisputeList;
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
import bisq.core.dao.state.DaoStateStore;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputList;
import bisq.core.payment.AccountAgeWitnessStore;
import bisq.core.payment.PaymentAccountList;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.TradableList;
import bisq.core.trade.statistics.TradeStatistics2Store;
import bisq.core.user.PreferencesPayload;
import bisq.core.user.UserPayload;

import bisq.network.p2p.peers.peerexchange.PeerList;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadList;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.NavigationPath;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import io.bisq.generated.protobuffer.PB;

import com.google.inject.Provider;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

// TODO Use ProtobufferException instead of ProtobufferRuntimeException
@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<BtcWalletService> btcWalletService;
    private final NetworkProtoResolver networkProtoResolver;
    private final File storageDir;

    @Inject
    public CorePersistenceProtoResolver(Provider<BtcWalletService> btcWalletService,
                                        NetworkProtoResolver networkProtoResolver,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        this.btcWalletService = btcWalletService;
        this.networkProtoResolver = networkProtoResolver;
        this.storageDir = storageDir;

    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableEnvelope proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case SEQUENCE_NUMBER_MAP:
                    return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
                case PEER_LIST:
                    return PeerList.fromProto(proto.getPeerList());
                case ADDRESS_ENTRY_LIST:
                    return AddressEntryList.fromProto(proto.getAddressEntryList());
                case TRADABLE_LIST:
                    return TradableList.fromProto(proto.getTradableList(),
                            this,
                            new Storage<>(storageDir, this),
                            btcWalletService.get());
                case TRADE_STATISTICS_LIST:
                    throw new ProtobufferRuntimeException("TRADE_STATISTICS_LIST is not used anymore");
                case DISPUTE_LIST:
                    return DisputeList.fromProto(proto.getDisputeList(),
                            this,
                            new Storage<>(storageDir, this));
                case PREFERENCES_PAYLOAD:
                    return PreferencesPayload.fromProto(proto.getPreferencesPayload(), this);
                case USER_PAYLOAD:
                    return UserPayload.fromProto(proto.getUserPayload(), this);
                case NAVIGATION_PATH:
                    return NavigationPath.fromProto(proto.getNavigationPath());
                case PAYMENT_ACCOUNT_LIST:
                    return PaymentAccountList.fromProto(proto.getPaymentAccountList(), this);
                case PERSISTABLE_NETWORK_PAYLOAD_LIST:
                    return PersistableNetworkPayloadList.fromProto(proto.getPersistableNetworkPayloadList(), this);
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
                case MERIT_LIST:
                    return MeritList.fromProto(proto.getMeritList());
                case DAO_STATE_STORE:
                    return DaoStateStore.fromProto(proto.getDaoStateStore());
                case MY_REPUTATION_LIST:
                    return MyReputationList.fromProto(proto.getMyReputationList());
                case MY_PROOF_OF_BURN_LIST:
                    return MyProofOfBurnList.fromProto(proto.getMyProofOfBurnList());
                case UNCONFIRMED_BSQ_CHANGE_OUTPUT_LIST:
                    return UnconfirmedBsqChangeOutputList.fromProto(proto.getUnconfirmedBsqChangeOutputList());

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
