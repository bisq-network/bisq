package io.bitsquare.trade;

import com.google.inject.Inject;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.KeyPair;
import io.bitsquare.btc.MockWalletFacade;
import io.bitsquare.crypto.ICryptoFacade;
import io.bitsquare.msg.IMessageFacade;
import io.bitsquare.msg.Message;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

/**
 * Main facade for operating with trade domain between GUI and services (msg, btc)
 */
public class TradingFacade
{
    private static final Logger log = LoggerFactory.getLogger(TradingFacade.class);

    private final HashMap<String, Offer> offers = new HashMap<>();
    private final HashMap<String, Trade> trades = new HashMap<>();
    private final HashMap<String, Contract> contracts = new HashMap<>();
    private User user;
    private IMessageFacade messageFacade;
    private BlockChainFacade blockChainFacade;
    private MockWalletFacade walletFacade;
    private ICryptoFacade cryptoFacade;
    private Settings settings;

    @Inject
    public TradingFacade(User user,
                         Settings settings,
                         IMessageFacade messageFacade,
                         BlockChainFacade blockChainFacade,
                         MockWalletFacade walletFacade,
                         ICryptoFacade cryptoFacade)
    {
        this.user = user;
        this.settings = settings;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;
    }

    /**
     * @param offer
     */
    public void placeNewOffer(Offer offer)
    {
        log.info("place New Offer");
        offers.put(offer.getUid().toString(), offer);
        messageFacade.broadcast(new Message(Message.BROADCAST_NEW_OFFER, offer));
    }

    /**
     * Taker requests offerer to take the offer
     *
     * @param trade
     */
    public void sendTakeOfferRequest(Trade trade)
    {
        log.info("Taker asks offerer to take his offer");
        messageFacade.send(new Message(Message.REQUEST_TAKE_OFFER, trade), trade.getOffer().getOfferer().getMessageID());
    }


    /**
     * @param trade
     * @return
     */
    public Contract createNewContract(Trade trade)
    {
        log.info("create new contract");
        KeyPair address = walletFacade.createNewAddress();

        Contract contract = new Contract(trade, address.getPubKey());
        contract.setOfferer(trade.getOffer().getOfferer());
        contract.setTaker(user);
        contracts.put(trade.getUid().toString(), contract);
        return contract;
    }

    /**
     * @param contract
     */
    public void signContract(Contract contract)
    {
        log.info("sign Contract");

        String contractAsJson = Utils.convertToJson(contract);

        contract.getTrade().setJsonRepresentation(contractAsJson);
        contract.getTrade().setSignature(cryptoFacade.sign(contractAsJson));
    }

    /**
     * @param offer
     * @return
     */
    public Trade createNewTrade(Offer offer)
    {
        log.info("create New Trade");
        Trade trade = new Trade(offer);
        trades.put(trade.getUid().toString(), trade);
        return trade;
    }


    public HashMap<String, Trade> getTrades()
    {
        return trades;
    }

    /**
     * @param trade
     */
    public void payOfferFee(Trade trade)
    {
        log.info("Pay offer fee");

        trade.setTakeOfferFeePayed(true);

        String txID = UUID.randomUUID().toString();
        trade.setTakeOfferFeePayed(true);
        trade.setTakeOfferFeeTxID(txID);

        log.info("Taker asks offerer for confirmation for his fee payment. txID=" + txID);
        messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }


    public void requestOffererDetailData()
    {
        log.info("Request offerer detail data");

    }

    /**
     * @param trade
     */
    public void payToDepositTx(Trade trade)
    {
        log.info("create MultiSig address");

        log.info("Create deposit tx");

        log.info("Sign deposit tx");

        log.info("Send deposit Tx");
        messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }


    /**
     * @param trade
     */
    public void releaseBTC(Trade trade)
    {
        log.info("Sign payment tx");

        log.info("Broadcast payment tx");

        log.info("Send message to peer that payment Tx has been broadcasted.");
        messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }


}
