package io.bisq.core.proto.persistable;

import com.google.inject.Provider;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableViewPath;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.*;
import io.bisq.core.trade.statistics.TradeStatisticsList;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.PreferencesPayload;
import io.bisq.core.user.UserPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.PeerList;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;

@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Storage<TradableList<OpenOffer>> openOfferStorage;
    private final Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage;
    private final Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage;
    private final Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage;
    private final Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage;
    private final Provider<BtcWalletService> btcWalletService;
    private final NetworkProtoResolver networkProtoResolver;

    @Inject
    public CorePersistenceProtoResolver(Provider<BtcWalletService> btcWalletService,
                                        NetworkProtoResolver networkProtoResolver,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        this.btcWalletService = btcWalletService;
        this.networkProtoResolver = networkProtoResolver;

        openOfferStorage = new Storage<>(storageDir, this);
        buyerAsMakerTradeStorage = new Storage<>(storageDir, this);
        buyerAsTakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsMakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsTakerTradeStorage = new Storage<>(storageDir, this);
    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableEnvelope proto) {
        log.info("Convert protobuffer disk proto: {}", proto.getMessageCase());

        switch (proto.getMessageCase()) {
            case ADDRESS_ENTRY_LIST:
                return AddressEntryList.fromProto(proto.getAddressEntryList());
            case VIEW_PATH_AS_STRING:
                return PersistableViewPath.fromProto(proto.getViewPathAsString());
            case OPEN_OFFER_LIST:
                return TradableList.fromProto(proto.getOpenOfferList(), openOfferStorage);
            case TRADABLE_LIST:
                return TradableList.fromProto(proto.getTradableList(),
                        this,
                        openOfferStorage,
                        buyerAsMakerTradeStorage,
                        buyerAsTakerTradeStorage,
                        sellerAsMakerTradeStorage,
                        sellerAsTakerTradeStorage,
                        btcWalletService.get());
            case PEER_LIST:
                return PeerList.fromProto(proto.getPeerList());
            case COMPENSATION_REQUEST_PAYLOAD:
                // TODO There will be another object for PersistableEnvelope
                return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
            case PREFERENCES_PAYLOAD:
                return PreferencesPayload.fromProto(proto.getPreferencesPayload(), this);
            case USER_PAYLOAD:
                return UserPayload.fromProto(proto.getUserPayload(), this);
            case SEQUENCE_NUMBER_MAP:
                return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
            case TRADE_STATISTICS_LIST:
                return TradeStatisticsList.fromProto(proto.getTradeStatisticsList());
            case PERSISTED_ENTRY_MAP:
                return P2PDataStorage.fromProto(proto.getPersistedEntryMap().getPersistedEntryMapMap(),
                        networkProtoResolver);
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }

    public Tradable fromProto(PB.Tradable proto, Storage<TradableList<SellerAsMakerTrade>> storage) {
        log.error("Convert protobuffer disk proto: {}", proto.getMessageCase());

        switch (proto.getMessageCase()) {
            case OPEN_OFFER:
                return OpenOffer.fromProto(proto.getOpenOffer());
            case BUYER_AS_MAKER_TRADE:
                return BuyerAsMakerTrade.fromProto(proto.getBuyerAsMakerTrade(), storage, btcWalletService.get());
            case BUYER_AS_TAKER_TRADE:
                return BuyerAsTakerTrade.fromProto(proto.getBuyerAsTakerTrade(), storage, btcWalletService.get());
            case SELLER_AS_MAKER_TRADE:
                return SellerAsMakerTrade.fromProto(proto.getSellerAsMakerTrade(), storage, btcWalletService.get());
            case SELLER_AS_TAKER_TRADE:
                return SellerAsTakerTrade.fromProto(proto.getSellerAsTakerTrade(), storage, btcWalletService.get());
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }
}
