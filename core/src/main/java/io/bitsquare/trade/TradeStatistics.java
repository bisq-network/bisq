package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.security.PublicKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TradeStatistics implements StoragePayload {
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    public final Offer offer;
    public final long tradePriceAsLong;
    public final long tradeAmountAsLong;
    public final long tradeDateAsTime;
    public final String depositTxId;
    public final byte[] contractHash;
    public final PubKeyRing pubKeyRing;

    public final int protocolVersion;

    public TradeStatistics(Offer offer, Fiat tradePrice, Coin tradeAmount, Date tradeDate, String depositTxId, byte[] contractHash, PubKeyRing pubKeyRing) {
        this.offer = offer;
        this.depositTxId = depositTxId;
        this.tradePriceAsLong = tradePrice.longValue();
        tradeAmountAsLong = tradeAmount.value;
        this.tradeDateAsTime = tradeDate.getTime();
        this.contractHash = contractHash;
        this.pubKeyRing = pubKeyRing;

        protocolVersion = Version.TRADE_PROTOCOL_VERSION;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }
}
