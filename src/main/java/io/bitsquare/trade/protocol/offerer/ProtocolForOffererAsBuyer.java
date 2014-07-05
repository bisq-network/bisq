package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.taker.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.taker.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.taker.TakeOfferFeePayedMessage;
import io.bitsquare.user.User;
import java.math.BigInteger;
import net.tomp2p.peers.PeerAddress;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing from the peer.
 * <p>
 * This class handles the role of the offerer as the Bitcoin buyer.
 * <p>
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages need to be validated before further processing.
 */
public class ProtocolForOffererAsBuyer
{

    private static final Logger log = LoggerFactory.getLogger(ProtocolForOffererAsBuyer.class);
    private final String arbitratorPubKey;
    private final BigInteger collateral;
    private final BankAccount bankAccount;
    private final String accountId;
    private final BigInteger tradeAmount;
    private final String messagePubKey;
    private final ECKey accountKey;
    private final String payoutAddress;

    public enum State
    {
        Init,
        HandleTakeOfferRequest,

        onTakeOfferFeePayedMessage,
        VerifyTakeOfferFeePayment,
        CreateDepositTx,
        RequestTakerDepositPayment,

        onRequestOffererPublishDepositTxMessage,
        VerifyTakerAccount,
        VerifyAndSignContract,
        SignAndPublishDepositTx,
        SendDepositTxIdToTaker,
        SetupListenerForBlockChainConfirmation,
        onResultSetupListenerForBlockChainConfirmation,

        onUIEventBankTransferInited,
        SendSignedPayoutTx,

        onPayoutTxPublishedMessage
    }

    private final Trade trade;
    private final PeerAddress peerAddress;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final BlockChainFacade blockChainFacade;
    private final CryptoFacade cryptoFacade;
    private final ProtocolForOffererAsBuyerListener listener;

    private final String id;
    private final String tradeId;
    private final Offer offer;

    private State state;

    // data written/read by tasks
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;

    // data written by messages, read by tasks
    private String takeOfferFeeTxId;
    private String takerPubKey;
    private String peersPayoutAddress;
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private String peersMessagePubKey;
    private String peersContractAsJson;
    private String signedTakerDepositTxAsHex;
    private String txConnOutAsHex;
    private String txScriptSigAsHex;
    private long takerTxOutIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProtocolForOffererAsBuyer(Trade trade,
                                     PeerAddress peerAddress,
                                     MessageFacade messageFacade,
                                     WalletFacade walletFacade,
                                     BlockChainFacade blockChainFacade,
                                     CryptoFacade cryptoFacade,
                                     User user,
                                     ProtocolForOffererAsBuyerListener listener)
    {
        this.trade = trade;
        this.peerAddress = peerAddress;
        this.listener = listener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;

        id = trade.getId();

        tradeId = trade.getId();
        offer = trade.getOffer();
        arbitratorPubKey = offer.getArbitrator().getPubKeyAsHex();
        collateral = trade.getCollateralAmount();
        bankAccount = user.getBankAccount(trade.getOffer().getBankAccountId());
        accountId = user.getAccountId();
        tradeAmount = trade.getTradeAmount();
        messagePubKey = user.getMessagePubKeyAsHex();
        accountKey = walletFacade.getRegistrationAddressInfo().getKey();
        payoutAddress = walletFacade.getAddressInfoByTradeID(tradeId).getAddressString();
        state = State.Init;
    }


    public void start()
    {
        log.debug("start called ");
        HandleTakeOfferRequest.run(this::onResultHandleTakeOfferRequest, this::onFault, peerAddress, messageFacade, trade.getState(), tradeId);
        state = State.HandleTakeOfferRequest;
    }

    public void onResultHandleTakeOfferRequest(boolean takeOfferRequestAccepted)
    {
        log.debug("onResultHandleTakeOfferRequest called ");
        if (takeOfferRequestAccepted)
        {
            trade.setState(Trade.State.ACCEPTED);
            messageFacade.removeOffer(offer);
            listener.onOfferAccepted(offer);
            listener.onWaitingForPeerResponse(state);
        }
        else
        {
            log.info("Finish here as we have already the offer accepted.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTakeOfferFeePayedMessage(@NotNull TakeOfferFeePayedMessage message)
    {
        log.debug("onTakeOfferFeePayedMessage called ");

        // validation
        checkState(state == State.HandleTakeOfferRequest);
        checkArgument(tradeId.equals(message.getTradeId()));
        String takeOfferFeeTxId = nonEmptyStringOf(message.getTakeOfferFeeTxId());
        BigInteger tradeAmount = nonNegativeBigIntegerOf(nonZeroBigIntegerOf(message.getTradeAmount()));
        String takerPubKey = nonEmptyStringOf(message.getTakerPubKey());

        // apply new state
        state = State.onTakeOfferFeePayedMessage;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.takerPubKey = takerPubKey;
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);
        trade.setTradeAmount(tradeAmount);

        // next task
        VerifyTakeOfferFeePayment.run(this::onResultVerifyTakeOfferFeePayment, this::onFault, walletFacade, this.takeOfferFeeTxId);
        state = State.VerifyTakeOfferFeePayment;
    }

    public void onResultVerifyTakeOfferFeePayment()
    {
        log.debug("onResultVerifyTakeOfferFeePayment called ");

        CreateDepositTx.run(this::onResultCreateDepositTx, this::onFault, walletFacade, tradeId, collateral, takerPubKey, arbitratorPubKey);

        state = State.CreateDepositTx;
    }

    public void onResultCreateDepositTx(String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex)
    {
        log.debug("onResultCreateDepositTx called ");
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;


        RequestTakerDepositPayment.run(this::onResultRequestTakerDepositPayment,
                                       this::onFault,
                                       peerAddress,
                                       messageFacade,
                                       tradeId,
                                       bankAccount,
                                       accountId,
                                       offererPubKey,
                                       preparedOffererDepositTxAsHex,
                                       offererTxOutIndex);

        state = State.RequestTakerDepositPayment;
    }

    public void onResultRequestTakerDepositPayment()
    {
        log.debug("onResultRequestTakerDepositPayment called ");
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage message)
    {
        log.debug("onRequestOffererPublishDepositTxMessage called ");

        // validation
        checkState(state == State.RequestTakerDepositPayment);
        checkArgument(tradeId.equals(message.getTradeId()));
        String peersPayoutAddress = nonEmptyStringOf(message.getTakerPayoutAddress());
        String peersAccountId = nonEmptyStringOf(message.getTakerAccountId());
        BankAccount peersBankAccount = checkNotNull(message.getTakerBankAccount());
        String peersMessagePubKey = nonEmptyStringOf(message.getTakerMessagePubKey());
        String peersContractAsJson = nonEmptyStringOf(message.getTakerContractAsJson());
        String signedTakerDepositTxAsHex = nonEmptyStringOf(message.getSignedTakerDepositTxAsHex());
        String txConnOutAsHex = nonEmptyStringOf(message.getTxConnOutAsHex());
        String txScriptSigAsHex = nonEmptyStringOf(message.getTxScriptSigAsHex());
        long takerTxOutIndex = nonNegativeLongOf(message.getTakerTxOutIndex());

        // apply new state
        state = State.onRequestOffererPublishDepositTxMessage;
        this.peersPayoutAddress = peersPayoutAddress;
        this.peersAccountId = peersAccountId;
        this.peersBankAccount = peersBankAccount;
        this.peersMessagePubKey = peersMessagePubKey;
        this.peersContractAsJson = peersContractAsJson;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.takerTxOutIndex = takerTxOutIndex;

        // next task
        VerifyTakerAccount.run(this::onResultVerifyTakerAccount, this::onFault, blockChainFacade, this.peersAccountId, this.peersBankAccount);
        state = State.VerifyTakerAccount;
    }

    public void onResultVerifyTakerAccount()
    {
        log.debug("onResultVerifyTakerAccount called ");

        VerifyAndSignContract.run(this::onResultVerifyAndSignContract,
                                  this::onFault,
                                  cryptoFacade,
                                  accountId,
                                  tradeAmount,
                                  takeOfferFeeTxId,
                                  messagePubKey,
                                  offer,
                                  peersAccountId,
                                  bankAccount,
                                  peersBankAccount,
                                  peersMessagePubKey,
                                  peersContractAsJson,
                                  accountKey);

        state = State.VerifyAndSignContract;
    }

    public void onResultVerifyAndSignContract(Contract contract, String contractAsJson, String signature)
    {
        log.debug("onResultVerifyAndSignContract called ");

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        SignAndPublishDepositTx.run(this::onResultSignAndPublishDepositTx,
                                    this::onFault,
                                    walletFacade,
                                    preparedOffererDepositTxAsHex,
                                    signedTakerDepositTxAsHex,
                                    txConnOutAsHex,
                                    txScriptSigAsHex,
                                    offererTxOutIndex,
                                    takerTxOutIndex);
        state = State.SignAndPublishDepositTx;
    }

    public void onResultSignAndPublishDepositTx(Transaction depositTransaction)
    {
        log.debug("onResultSignAndPublishDepositTx called ");

        trade.setDepositTransaction(depositTransaction);
        listener.onDepositTxPublished(depositTransaction.getHashAsString());

        SendDepositTxIdToTaker.run(this::onResultSendDepositTxIdToTaker, this::onFault, peerAddress, messageFacade, tradeId, depositTransaction);
        state = State.SendDepositTxIdToTaker;
    }

    public void onResultSendDepositTxIdToTaker()
    {
        log.debug("onResultSendDepositTxIdToTaker called ");

        SetupListenerForBlockChainConfirmation.run(this::onResultSetupListenerForBlockChainConfirmation, this::onFault, trade.getDepositTransaction(), listener);
        state = State.SetupListenerForBlockChainConfirmation;
    }

    public void onResultSetupListenerForBlockChainConfirmation()
    {
        log.debug("onResultSetupListenerForBlockChainConfirmation called ");

        state = State.onResultSetupListenerForBlockChainConfirmation;
        listener.onWaitingForUserInteraction(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Triggered from UI event: Button click "Bank transfer inited"
    public void onUIEventBankTransferInited()
    {
        log.debug("onUIEventBankTransferInited called ");

        // validation
        checkState(state == State.onResultSetupListenerForBlockChainConfirmation);


        state = State.onUIEventBankTransferInited;

        // next task
        String depositTransactionId = trade.getDepositTransaction().getHashAsString();

        SendSignedPayoutTx.run(this::onResultSendSignedPayoutTx,
                               this::onFault,
                               peerAddress,
                               messageFacade,
                               walletFacade,
                               tradeId,
                               peersPayoutAddress,
                               payoutAddress,
                               depositTransactionId,
                               collateral,
                               tradeAmount);
        state = State.SendSignedPayoutTx;
    }

    public void onResultSendSignedPayoutTx()
    {
        log.debug("onResultSendSignedPayoutTx called ");

        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPayoutTxPublishedMessage(PayoutTxPublishedMessage message)
    {
        log.debug("onPayoutTxPublishedMessage called ");

        // validation
        checkState(state == State.SendSignedPayoutTx);
        checkArgument(tradeId.equals(message.getTradeId()));
        String payoutTxAsHex = nonEmptyStringOf(message.getPayoutTxAsHex());

        state = State.onPayoutTxPublishedMessage;

        // next task
        listener.onPayoutTxPublished(payoutTxAsHex);
        listener.onCompleted(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return id;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // generic fault handler
    private void onFault(Throwable throwable)
    {
        listener.onFault(throwable, state);
    }


}
