package io.bitsquare.trade;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.Fees;
import io.bitsquare.btc.KeyPair;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.Message;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

/**
 * Represents trade domain. Keeps complexity of process apart from view controller
 */
//TODO use scheduler/process pattern with tasks for every async job
public class Trading
{
    private static final Logger log = LoggerFactory.getLogger(Trading.class);

    private final HashMap<String, Offer> offers = new HashMap<>();
    private final HashMap<String, Trade> trades = new HashMap<>();
    private final HashMap<String, Contract> contracts = new HashMap<>();
    private User user;
    private MessageFacade messageFacade;
    private BlockChainFacade blockChainFacade;
    private WalletFacade walletFacade;
    private CryptoFacade cryptoFacade;
    private Settings settings;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Trading(User user,
                   Settings settings,
                   MessageFacade messageFacade,
                   BlockChainFacade blockChainFacade,
                   WalletFacade walletFacade,
                   CryptoFacade cryptoFacade)
    {
        this.user = user;
        this.settings = settings;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void placeNewOffer(Offer offer, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        log.info("place New Offer");
        offers.put(offer.getUid().toString(), offer);
        walletFacade.payFee(Fees.OFFER_CREATION_FEE, callback);

        messageFacade.broadcast(new Message(Message.BROADCAST_NEW_OFFER, offer));
    }

    public Trade createTrade(Offer offer)
    {
        log.info("create New Trade");
        Trade trade = new Trade(offer);
        trades.put(trade.getUid().toString(), trade);
        return trade;
    }

    public Contract createContract(Trade trade)
    {
        log.info("create new contract");
        KeyPair address = new KeyPair(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        //TODO
        Contract contract = new Contract(user, trade, address.getPubKey());
        contracts.put(trade.getUid().toString(), contract);
        return contract;
    }


    // trade process
    // 1
    public void sendTakeOfferRequest(Trade trade)
    {
        log.info("Taker asks offerer to take his offer");
        //messageFacade.send(new Message(Message.REQUEST_TAKE_OFFER, trade), trade.getOffer().getOfferer().getMessageID());
    }

    // 2
    public void payOfferFee(Trade trade, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        log.info("Pay offer fee");

        walletFacade.payFee(Fees.OFFER_TAKER_FEE, callback);


        log.info("Taker asks offerer for confirmation for his fee payment.");
        // messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }

    // 3
    public void requestOffererDetailData()
    {
        log.info("Request offerer detail data");

    }

    // 4
    public void signContract(Contract contract)
    {
        log.info("sign Contract");

        String contractAsJson = Utils.objectToJson(contract);

        contract.getTrade().setJsonRepresentation(contractAsJson);
        contract.getTrade().setSignature(cryptoFacade.signContract(walletFacade.getAccountKey(), contractAsJson));
    }

    // 5
    public void payToDepositTx(Trade trade)
    {
        //walletFacade.takerAddPaymentAndSign();
        // messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }

    // 6
    public void releaseBTC(Trade trade)
    {
        log.info("Sign payment tx");

        log.info("Broadcast payment tx");

        log.info("Send message to peer that payment Tx has been broadcasted.");
        // messageFacade.send(new Message(Message.REQUEST_OFFER_FEE_PAYMENT_CONFIRM, trade), trade.getOffer().getOfferer().getMessageID());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public HashMap<String, Trade> getTrades()
    {
        return trades;
    }

}
