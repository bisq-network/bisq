package io.bitsquare.trade;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Utils;
import com.google.inject.Inject;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.TradeMessage;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.payment.offerer.OffererPaymentProtocol;
import io.bitsquare.trade.payment.offerer.OffererPaymentProtocolListener;
import io.bitsquare.trade.payment.taker.TakerPaymentProtocol;
import io.bitsquare.trade.payment.taker.TakerPaymentProtocolListener;
import io.bitsquare.user.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents trade domain. Keeps complexity of process apart from view controller
 */
public class Trading
{
    private static final Logger log = LoggerFactory.getLogger(Trading.class);

    private Map<String, Offer> myOffers = new HashMap<>();
    private Map<String, Trade> trades = new HashMap<>();
    private final Map<String, TakerPaymentProtocol> takerPaymentProtocols = new HashMap<>();
    private final Map<String, OffererPaymentProtocol> offererPaymentProtocols = new HashMap<>();
    private final String storageKey;
    private User user;
    private Storage storage;
    private MessageFacade messageFacade;
    private BlockChainFacade blockChainFacade;
    private WalletFacade walletFacade;
    private CryptoFacade cryptoFacade;
    private Settings settings;
    private final StringProperty newTradeProperty = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Trading(User user,
                   Settings settings,
                   Storage storage,
                   MessageFacade messageFacade,
                   BlockChainFacade blockChainFacade,
                   WalletFacade walletFacade,
                   CryptoFacade cryptoFacade)
    {
        this.user = user;
        this.settings = settings;
        this.storage = storage;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;

        storageKey = this.getClass().getName();

        Object offersObject = storage.read(storageKey + ".offers");
        if (offersObject != null && offersObject instanceof HashMap)
            myOffers = (Map) offersObject;

        Object tradesObject = storage.read(storageKey + ".trades");
        if (tradesObject != null && tradesObject instanceof HashMap)
            trades = (Map) tradesObject;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup()
    {
    }

    private void saveOffers()
    {
        storage.write(storageKey + ".offers", myOffers);
    }

    public void addOffer(Offer offer) throws IOException
    {
        if (myOffers.containsKey(offer.getId()))
            throw new IllegalStateException("offers contains already a offer with the ID " + offer.getId());

        myOffers.put(offer.getId(), offer);
        saveOffers();

        messageFacade.addOffer(offer);
    }

    public void removeOffer(Offer offer) throws IOException
    {
        myOffers.remove(offer.getId());
        saveOffers();

        messageFacade.removeOffer(offer);
    }

    public Trade createTrade(Offer offer)
    {
        if (trades.containsKey(offer.getId()))
            throw new IllegalStateException("trades contains already a trade with the ID " + offer.getId());

        Trade trade = new Trade(offer);
        trades.put(offer.getId(), trade);
        //TODO for testing
        //storage.write(storageKey + ".trades", trades);

        this.newTradeProperty.set(trade.getId());

        return trade;
    }

    public void removeTrade(Trade trade) throws IOException
    {
        trades.remove(trade.getId());
        storage.write(storageKey + ".trades", trades);
    }

    public final StringProperty getNewTradeProperty()
    {
        return this.newTradeProperty;
    }

    public TakerPaymentProtocol addTakerPaymentProtocol(Trade trade, TakerPaymentProtocolListener listener)
    {
        TakerPaymentProtocol takerPaymentProtocol = new TakerPaymentProtocol(trade, listener, messageFacade, walletFacade, blockChainFacade, cryptoFacade, user);
        takerPaymentProtocols.put(trade.getId(), takerPaymentProtocol);
        return takerPaymentProtocol;
    }

    public OffererPaymentProtocol addOffererPaymentProtocol(Trade trade, OffererPaymentProtocolListener listener)
    {
        OffererPaymentProtocol offererPaymentProtocol = new OffererPaymentProtocol(trade, listener, messageFacade, walletFacade, blockChainFacade, cryptoFacade, user);
        offererPaymentProtocols.put(trade.getId(), offererPaymentProtocol);
        return offererPaymentProtocol;
    }

    public void createOffererPaymentProtocol(TradeMessage tradeMessage, PeerAddress sender)
    {
        Offer offer = myOffers.get(tradeMessage.getOfferUID());
        Trade trade = createTrade(offer);
        OffererPaymentProtocol offererPaymentProtocol = addOffererPaymentProtocol(trade, new OffererPaymentProtocolListener()
        {
            @Override
            public void onProgress(double progress)
            {
                //log.debug("onProgress " + progress);
            }

            @Override
            public void onFailure(String failureMessage)
            {
                log.warn(failureMessage);
            }

            @Override
            public void onDepositTxPublished(String depositTxID)
            {
                log.debug("trading onDepositTxPublished " + depositTxID);
            }

            @Override
            public void onDepositTxConfirmedUpdate(TransactionConfidence confidence)
            {
                log.debug("trading onDepositTxConfirmedUpdate");
            }

            @Override
            public void onPayoutTxPublished(String payoutTxAsHex)
            {
                Transaction payoutTx = new Transaction(walletFacade.getWallet().getParams(), Utils.parseAsHexOrBase58(payoutTxAsHex));
                trade.setPayoutTransaction(payoutTx);
                trade.setState(Trade.State.COMPLETED);
                log.debug("trading onPayoutTxPublished");
            }

            @Override
            public void onDepositTxConfirmedInBlockchain()
            {
                log.debug("trading onDepositTxConfirmedInBlockchain");
            }

        });

        // the handler was not called there because the obejct was not created when the event occurred (and therefor no listener)
        // will probably created earlier, so let it for the moment like that....
        offererPaymentProtocol.onTakeOfferRequested(sender);
    }


    public void onBankTransferInited(String tradeUID)
    {
        offererPaymentProtocols.get(tradeUID).bankTransferInited();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////


    // 6
    public void releaseBTC(String tradeUID, TradeMessage tradeMessage)
    {
        takerPaymentProtocols.get(tradeUID).releaseBTC(tradeMessage);
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
        return myOffers;
    }

    public Offer getOffer(String offerUID)
    {
        return myOffers.get(offerUID.toString());
    }
}
