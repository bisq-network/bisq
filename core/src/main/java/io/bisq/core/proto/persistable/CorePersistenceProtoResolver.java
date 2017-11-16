package io.bisq.core.proto.persistable;

import com.google.inject.Provider;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.NavigationPath;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.arbitration.DisputeList;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.dao.vote.VoteItemsList;
import io.bisq.core.payment.PaymentAccountList;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.TradableList;
import io.bisq.core.user.PreferencesPayload;
import io.bisq.core.user.UserPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.PeerList;
import io.bisq.network.p2p.storage.PersistableEntryMap;
import io.bisq.network.p2p.storage.PersistableNetworkPayloadCollection;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;

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
                case PERSISTED_ENTRY_MAP:
                    return PersistableEntryMap.fromProto(proto.getPersistedEntryMap().getPersistedEntryMapMap(),
                            networkProtoResolver);
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
                    throw new ProtobufferException("TRADE_STATISTICS_LIST is not used anymore");
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
                case COMPENSATION_REQUEST_PAYLOAD:
                    return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
                case VOTE_ITEMS_LIST:
                    return VoteItemsList.fromProto(proto.getVoteItemsList());
                case BSQ_CHAIN_STATE:
                    return BsqChainState.fromProto(proto.getBsqChainState());
                case PERSISTABLE_NETWORK_PAYLOAD_LIST:
                    return PersistableNetworkPayloadCollection.fromProto(proto.getPersistableNetworkPayloadList(), this);
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.PersistableEnvelope). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PersistableEnvelope is null");
            throw new ProtobufferException("PB.PersistableEnvelope is null");
        }
    }
}
