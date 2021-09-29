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

package bisq.core.trade.protocol.bsqswap;

import bisq.core.btc.BsqSwapTxHelper;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.atomic.messages.CreateAtomicTxResponse;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.model.trade.TradeManager;
import bisq.core.trade.protocol.TradeProtocolModel;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.trade.ProcessModelServiceProvider;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.taskrunner.Model;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// Fields marked as transient are only used during protocol execution which are based on directMessages so we do not
// persist them.

/**
 * This is the base model for the trade protocol. It is persisted with the trade (non transient fields).
 * It uses the {@link ProcessModelServiceProvider} for access to domain services.
 */

@Getter
@Slf4j
public class BsqSwapProtocolModel implements TradeProtocolModel, Model, PersistablePayload {
    transient private ProcessModelServiceProvider provider;
    transient private TradeManager tradeManager;
    transient private Offer offer;

    private BsqSwapTrade bsqSwapTrade;

    private final TradingPeer tradingPeer;
    private final PubKeyRing pubKeyRing;
    // Copy to trade.tradingPeerAddress after successful verification of incoming message
    @Nullable
    @Setter
    private NodeAddress tempTradingPeerNodeAddress;
    @Setter
    transient private TradeMessage tradeMessage;
    @Setter
    private BsqSwapTxHelper bsqSwapTxHelper;

    @Setter
    private long bsqTradeAmount;
    @Setter
    private long bsqMaxTradeAmount;
    @Setter
    private long bsqMinTradeAmount;
    @Setter
    private long btcTradeAmount;
    @Setter
    private long btcMaxTradeAmount;
    @Setter
    private long btcMinTradeAmount;
    @Setter
    private long tradePrice;
    @Setter
    private long bsqTakerTradeFee;
    @Setter
    private long bsqMakerTradeFee;
    @Setter
    private long txFeePerVbyte;
    @Setter
    private long txFee;
    @Setter
    private long takerBsqOutputAmount;
    @Setter
    private long takerBtcOutputAmount;
    @Setter
    private long makerBsqOutputAmount;
    @Setter
    private long makerBtcOutputAmount;
    @Nullable
    @Setter
    private String takerBsqAddress;
    @Nullable
    @Setter
    private String takerBtcAddress;
    @Nullable
    @Setter
    private String makerBsqAddress;
    @Nullable
    @Setter
    private String makerBtcAddress;
    @Setter
    private List<RawTransactionInput> rawTakerBsqInputs = new ArrayList<>();
    @Setter
    private List<RawTransactionInput> rawTakerBtcInputs = new ArrayList<>();
    @Setter
    private List<RawTransactionInput> rawMakerBsqInputs = new ArrayList<>();
    @Setter
    private List<RawTransactionInput> rawMakerBtcInputs = new ArrayList<>();
    @Nullable
    @Setter
    private byte[] atomicTx;
    @Nullable
    @Setter
    private Transaction verifiedAtomicTx;

    public BsqSwapProtocolModel(PubKeyRing pubKeyRing) {
        this(pubKeyRing, new TradingPeer());
    }

    public BsqSwapProtocolModel(PubKeyRing pubKeyRing, TradingPeer tradingPeer) {
        this.pubKeyRing = pubKeyRing;
        this.tradingPeer = tradingPeer != null ? tradingPeer : new TradingPeer();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BsqSwapProtocolModel toProtoMessage() {
        protobuf.BsqSwapProtocolModel.Builder builder = protobuf.BsqSwapProtocolModel.newBuilder()
                .setTradingPeer((protobuf.TradingPeer) tradingPeer.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage());
        Optional.ofNullable(tempTradingPeerNodeAddress).
                ifPresent(e -> builder.setTempTradingPeerNodeAddress(e.toProtoMessage()));
        return builder.build();
    }

    public static BsqSwapProtocolModel fromProto(protobuf.BsqSwapProtocolModel proto,
                                                 CoreProtoResolver coreProtoResolver) {
        TradingPeer tradingPeer = TradingPeer.fromProto(proto.getTradingPeer(), coreProtoResolver);
        PubKeyRing pubKeyRing = PubKeyRing.fromProto(proto.getPubKeyRing());
        BsqSwapProtocolModel bsqSwapProtocolModel = new BsqSwapProtocolModel(pubKeyRing, tradingPeer);
        bsqSwapProtocolModel.setTempTradingPeerNodeAddress(proto.hasTempTradingPeerNodeAddress() ?
                NodeAddress.fromProto(proto.getTempTradingPeerNodeAddress()) : null);
        return bsqSwapProtocolModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeProtocolModel
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void applyTransient(ProcessModelServiceProvider provider,
                               TradeManager tradeManager,
                               Offer offer) {
        this.offer = offer;
        this.provider = provider;
        this.tradeManager = tradeManager;
    }

    @Override
    public P2PService getP2PService() {
        return provider.getP2PService();
    }

    @Override
    public NodeAddress getMyNodeAddress() {
        return getP2PService().getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
    }

    public void initFromTrade(BsqSwapTrade trade) {
        bsqSwapTrade = trade;
        var offer = trade.getOffer();
        checkNotNull(offer, "offer must not be null");
        if (trade.getAmount() != null && trade.getAmount().isPositive())
            bsqAmountFromVolume(trade.getOffer().getVolume()).ifPresent(this::setBsqTradeAmount);
        bsqAmountFromVolume(offer.getVolume()).ifPresent(this::setBsqMaxTradeAmount);
        bsqAmountFromVolume(offer.getMinVolume()).ifPresent(this::setBsqMinTradeAmount);
        // Atomic trades only allow fixed prices
        var price = offer.isUseMarketBasedPrice() ? 0 : Objects.requireNonNull(offer.getPrice()).getValue();
        setTradePrice(price);
        if (trade.getAmount() != null && trade.getAmount().isPositive())
            setBtcTradeAmount(trade.getAmount().getValue());
        setBtcMaxTradeAmount(offer.getAmount().getValue());
        setBtcMinTradeAmount(offer.getMinAmount().getValue());
        setBsqTakerTradeFee(trade.getTakerFee());
        setBsqMakerTradeFee(trade.getMakerFee());
    }

    public void updateFromMessage(CreateAtomicTxRequest message) {
        setTakerBsqOutputAmount(message.getTakerBsqOutputValue());
        setTakerBtcOutputAmount(message.getTakerBtcOutputValue());
        setTakerBsqAddress(message.getTakerBsqOutputAddress());
        setTakerBtcAddress(message.getTakerBtcOutputAddress());
        setRawTakerBsqInputs(message.getTakerBsqInputs());
        setRawTakerBtcInputs(message.getTakerBtcInputs());
        setBsqTradeAmount(message.getBsqTradeAmount());
        setBtcTradeAmount(message.getBtcTradeAmount());
        tradingPeer.setPubKeyRing(checkNotNull(message.getTakerPubKeyRing()));
        bsqSwapTrade.setAmount(Coin.valueOf(message.getBtcTradeAmount()));
        bsqSwapTrade.setPeerNodeAddress(tempTradingPeerNodeAddress);
    }

    public void updateFromMessage(CreateAtomicTxResponse message) {
        setMakerBsqOutputAmount(message.getMakerBsqOutputValue());
        setMakerBsqAddress(message.getMakerBsqOutputAddress());
        setMakerBtcOutputAmount(message.getMakerBtcOutputValue());
        setMakerBtcAddress(message.getMakerBtcOutputAddress());
        setRawMakerBsqInputs(message.getMakerBsqInputs());
        setRawMakerBtcInputs(message.getMakerBtcInputs());
    }

    public Optional<Long> bsqAmountFromVolume(Volume volume) {
        // The Altcoin class have the smallest unit set to 8 decimals, BSQ has the smallest unit at 2 decimals.
        return volume == null ? Optional.empty() : Optional.of((volume.getMonetary().getValue() + 500_000) / 1_000_000);
    }

    public BsqWalletService getBsqWalletService() {
        return provider.getBsqWalletService();
    }

    public BtcWalletService getBtcWalletService() {
        return provider.getBtcWalletService();
    }

    public TradeWalletService getTradeWalletService() {
        return provider.getTradeWalletService();
    }

    public WalletsManager getWalletsManager() {
        return provider.getWalletsManager();
    }

    public DaoFacade getDaoFacade() {
        return provider.getDaoFacade();
    }

    public KeyRing getKeyRing() {
        return provider.getKeyRing();
    }

    public FilterManager getFilterManager() {
        return provider.getFilterManager();
    }

    public OpenOfferManager getOpenOfferManager() {
        return provider.getOpenOfferManager();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initTxBuilder(boolean isMaker) {
        bsqSwapTxHelper = new BsqSwapTxHelper(
                getBsqWalletService(),
                getTradeWalletService(),
                offer.isBuyOffer() == isMaker,
                bsqSwapTrade.getPrice(),
                bsqSwapTrade.getAmount(),
                Coin.valueOf(txFeePerVbyte),
                isMaker ? makerBtcAddress : takerBtcAddress,
                isMaker ? makerBsqAddress : takerBsqAddress
        );

        if (isMaker) {
            bsqSwapTxHelper.setMyTradeFee(Coin.valueOf(bsqSwapTrade.getMakerFee()));
            bsqSwapTxHelper.setPeerTradeFee(Coin.valueOf(bsqSwapTrade.getTakerFee()));
        } else {
            bsqSwapTxHelper.setMyTradeFee(Coin.valueOf(bsqSwapTrade.getTakerFee()));
            bsqSwapTxHelper.setPeerTradeFee(Coin.valueOf(bsqSwapTrade.getMakerFee()));
        }
    }

    public boolean takerPreparesTakerSide() {
        var mySideTxData = bsqSwapTxHelper.buildMySide(0, null, true);
        if (mySideTxData == null) {
            return false;
        }

        takerBsqOutputAmount = mySideTxData.bsqOutput.getValue();
        takerBtcOutputAmount = mySideTxData.btcOutput.getValue();
        rawTakerBsqInputs = mySideTxData.bsqInputs;
        rawTakerBtcInputs = mySideTxData.btcInputs;

        return true;
    }

    public boolean makerPreparesMakerSide() {
        var mySideTxData = bsqSwapTxHelper.buildMySide(0, null, false);
        if (mySideTxData == null) {
            return false;
        }

        makerBsqOutputAmount = mySideTxData.bsqOutput.getValue();
        makerBtcOutputAmount = mySideTxData.btcOutput.getValue();
        rawMakerBsqInputs = mySideTxData.bsqInputs;
        rawMakerBtcInputs = mySideTxData.btcInputs;

        return true;
    }

    public Transaction createAtomicTx() {
        return getTradeWalletService().createAtomicTx(
                Coin.valueOf(makerBsqOutputAmount),
                Coin.valueOf(makerBtcOutputAmount),
                Coin.valueOf(takerBsqOutputAmount),
                Coin.valueOf(takerBtcOutputAmount),
                makerBsqAddress,
                makerBtcAddress,
                takerBsqAddress,
                takerBtcAddress,
                rawMakerBsqInputs,
                rawMakerBtcInputs,
                rawTakerBsqInputs,
                rawTakerBtcInputs);
    }

    public long numTakerInputs() {
        return rawTakerBsqInputs.size() + rawTakerBtcInputs.size();
    }

    public long numMakerInputs() {
        return rawMakerBsqInputs.size() + rawMakerBtcInputs.size();
    }
}
