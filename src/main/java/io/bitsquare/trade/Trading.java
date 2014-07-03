package io.bitsquare.trade;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Utils;
import com.google.inject.Inject;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.payment.offerer.OffererAsBuyerProtocol;
import io.bitsquare.trade.payment.offerer.OffererAsBuyerProtocolListener;
import io.bitsquare.trade.payment.taker.TakerAsSellerProtocol;
import io.bitsquare.trade.payment.taker.TakerAsSellerProtocolListener;
import io.bitsquare.user.User;
import io.nucleo.scheduler.worker.Worker;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents trade domain. Keeps complexity of process apart from view controller
 */
@SuppressWarnings("EmptyMethod")
public class Trading
{
    private static final Logger log = LoggerFactory.getLogger(Trading.class);
    private final Map<String, TakerAsSellerProtocol> takerPaymentProtocols = new HashMap<>();
    private final Map<String, OffererAsBuyerProtocol> offererPaymentProtocols = new HashMap<>();
    private final String storageKey;
    private final User user;

    private final Storage storage;
    private final MessageFacade messageFacade;
    private final BlockChainFacade blockChainFacade;
    private final WalletFacade walletFacade;
    private final CryptoFacade cryptoFacade;
    private final StringProperty newTradeProperty = new SimpleStringProperty();

    private Map<String, Offer> offers = new HashMap<>();

    private Map<String, Trade> trades = new HashMap<>();
    private Trade currentPendingTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    @Inject
    public Trading(User user,
                   Storage storage,
                   MessageFacade messageFacade,
                   BlockChainFacade blockChainFacade,
                   WalletFacade walletFacade,
                   CryptoFacade cryptoFacade)
    {
        this.user = user;
        this.storage = storage;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;

        storageKey = this.getClass().getName();

        Object offersObject = storage.read(storageKey + ".offers");
        if (offersObject instanceof HashMap)
            offers = (Map<String, Offer>) offersObject;

        Object tradesObject = storage.read(storageKey + ".trades");
        if (tradesObject instanceof HashMap)
            trades = (Map<String, Trade>) tradesObject;

        messageFacade.addTakeOfferRequestListener((offerId, sender) -> createOffererAsBuyerProtocol(offerId, sender));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup()
    {
    }

    private void saveOffers()
    {
        storage.write(storageKey + ".offers", offers);
    }

    public void addOffer(Offer offer) throws IOException
    {
        if (offers.containsKey(offer.getId()))
            throw new IllegalStateException("offers contains already a offer with the ID " + offer.getId());

        offers.put(offer.getId(), offer);
        saveOffers();

        messageFacade.addOffer(offer);
    }

    public void removeOffer(Offer offer) throws IOException
    {
        offers.remove(offer.getId());
        saveOffers();

        messageFacade.removeOffer(offer);
    }


    public Trade createTrade(Offer offer)
    {
        Trade trade = new Trade(offer);
        trades.put(offer.getId(), trade);
        //TODO for testing
        //storage.write(storageKey + ".trades", trades);

        this.newTradeProperty.set(trade.getId());

        return trade;
    }

    public void removeTrade(Trade trade)
    {
        trades.remove(trade.getId());
        storage.write(storageKey + ".trades", trades);
    }


    public final StringProperty getNewTradeProperty()
    {
        return this.newTradeProperty;
    }

    public Trade takeOffer(BigInteger amount, Offer offer, TakerAsSellerProtocolListener listener, WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        Trade trade = createTrade(offer);
        trade.setTradeAmount(amount);

        TakerAsSellerProtocol takerPaymentProtocol = new TakerAsSellerProtocol(trade, listener, resultHandler, faultHandler, messageFacade, walletFacade, blockChainFacade, cryptoFacade, user);
        takerPaymentProtocols.put(trade.getId(), takerPaymentProtocol);

        return trade;
    }


    public void createOffererAsBuyerProtocol(String offerId, PeerAddress sender)
    {
        log.trace("createOffererAsBuyerProtocol offerId = " + offerId);
        Offer offer = offers.get(offerId);
        if (offer != null && offers.containsKey(offer.getId()))
        {
            offers.remove(offer);

            currentPendingTrade = createTrade(offer);
            OffererAsBuyerProtocolListener listener = new OffererAsBuyerProtocolListener()
            {
                @Override
                public void onDepositTxPublished(String depositTxID)
                {
                    log.trace("trading onDepositTxPublishedMessage " + depositTxID);
                }

                @Override
                public void onDepositTxConfirmedUpdate(TransactionConfidence confidence)
                {
                    log.trace("trading onDepositTxConfirmedUpdate");
                }

                @Override
                public void onPayoutTxPublished(String payoutTxAsHex)
                {
                    Transaction payoutTx = new Transaction(walletFacade.getWallet().getParams(), Utils.parseAsHexOrBase58(payoutTxAsHex));
                    currentPendingTrade.setPayoutTransaction(payoutTx);
                    currentPendingTrade.setState(Trade.State.COMPLETED);
                    log.debug("trading onPayoutTxPublishedMessage");
                }

                @Override
                public void onDepositTxConfirmedInBlockchain()
                {
                    log.trace("trading onDepositTxConfirmedInBlockchain");
                }

            };

            WorkerResultHandler resultHandler = new WorkerResultHandler()
            {
                @Override
                public void onResult(Worker worker)
                {
                    //log.trace("onResult " + worker.toString());
                }
            };
            WorkerFaultHandler faultHandler = new WorkerFaultHandler()
            {
                @Override
                public void onFault(Throwable throwable)
                {
                    log.error("onFault " + throwable);
                }
            };

            OffererAsBuyerProtocol offererAsBuyerProtocol = new OffererAsBuyerProtocol(currentPendingTrade, sender, messageFacade, walletFacade, blockChainFacade, cryptoFacade, user, resultHandler, faultHandler, listener);
            offererPaymentProtocols.put(currentPendingTrade.getId(), offererAsBuyerProtocol);
        }
        else
        {
            log.warn("Incoming offer take request does not match with any saved offer. We ignore that request.");
        }
    }


    public void onUIEventBankTransferInited(String tradeUID)
    {
        offererPaymentProtocols.get(tradeUID).onUIEventBankTransferInited();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////


    // 6
    public void releaseBTC(String tradeUID)
    {
        takerPaymentProtocols.get(tradeUID).onUIEventFiatReceived();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public Map<String, Trade> getTrades()
    {
        return trades;
    }


    public Map<String, Offer> getOffers()
    {
        return offers;
    }

    public Offer getOffer(String offerId)
    {
        return offers.get(offerId);
    }


    public boolean isOfferAlreadyInTrades(Offer offer)
    {
        return trades.containsKey(offer.getId());
    }

    public Trade getCurrentPendingTrade()
    {
        return currentPendingTrade;
    }
}
