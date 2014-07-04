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
import io.bitsquare.trade.protocol.messages.taker.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.messages.taker.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.messages.taker.TakeOfferFeePayedMessage;
import io.bitsquare.trade.protocol.tasks.offerer.*;
import io.bitsquare.user.User;
import java.math.BigInteger;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.bitsquare.util.Validator.*;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing from the peer.
 * That class handles the role of the offerer as the Bitcoin buyer.
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages as well data used to send to the peer need to be validated before further processing.
 */
public class OffererAsBuyerProtocol
{

    private static final Logger log = LoggerFactory.getLogger(OffererAsBuyerProtocol.class);
    public final PeerAddress peerAddress;
    // provided data
    private final String id;
    private final Trade trade;
    private final OffererAsBuyerProtocolListener listener;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final BlockChainFacade blockChainFacade;
    private final CryptoFacade cryptoFacade;
    private final User user;
    private final String tradeId;
    private final Offer offer;

    // data written/read by tasks
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;
    // data written by messages, read by tasks
    private String takeOfferFeeTxId;
    private String takerMultiSigPubKey;
    private String takerPayoutAddress;
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private String takerMessagePubKey;
    private String peersContractAsJson;
    private String signedTakerDepositTxAsHex;
    private String txConnOutAsHex;
    private String txScriptSigAsHex;
    private long takerTxOutIndex;
    // private
    private State state;

    public OffererAsBuyerProtocol(Trade trade,
                                  PeerAddress peerAddress,
                                  MessageFacade messageFacade,
                                  WalletFacade walletFacade,
                                  BlockChainFacade blockChainFacade,
                                  CryptoFacade cryptoFacade,
                                  User user,
                                  OffererAsBuyerProtocolListener listener)
    {
        this.trade = trade;
        this.peerAddress = peerAddress;
        this.listener = listener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;
        this.user = user;

        id = trade.getId();

        tradeId = trade.getId();
        offer = trade.getOffer();

        state = State.Init;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start()
    {
        log.debug("start");
        HandleTakeOfferRequest.run(this::onResultHandleTakeOfferRequest, this::onFault, peerAddress, messageFacade, trade.getState(), tradeId);
        state = State.HandleTakeOfferRequest;
    }

    public void onResultHandleTakeOfferRequest(Trade.State tradeState)
    {
        log.debug("onResultHandleTakeOfferRequest");
        trade.setState(tradeState);
        messageFacade.removeOffer(offer);
        listener.onOfferAccepted(offer);
        listener.onWaitingForPeerResponse(state);
    }

    public void onTakeOfferFeePayedMessage(TakeOfferFeePayedMessage message)
    {
        log.debug("onTakeOfferFeePayedMessage");

        // validation
        checkState(state == State.HandleTakeOfferRequest);
        checkNotNull(message);
        String takeOfferFeeTxId = nonEmptyStringOf(message.getTakeOfferFeeTxId());
        BigInteger tradeAmount = nonNegativeBigIntegerOf(nonZeroBigIntegerOf(message.getTradeAmount()));
        String takerMultiSigPubKey = nonEmptyStringOf(message.getTakerMultiSigPubKey());

        // apply new state
        state = State.onTakeOfferFeePayedMessage;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);
        trade.setTradeAmount(tradeAmount);

        // next task
        VerifyTakeOfferFeePayment.run(this::onResultVerifyTakeOfferFeePayment, this::onFault, walletFacade, takeOfferFeeTxId);
        state = State.VerifyTakeOfferFeePayment;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onResultVerifyTakeOfferFeePayment()
    {
        log.debug("onResultVerifyTakeOfferFeePayment");
        BigInteger collateralAmount = trade.getCollateralAmount();
        String arbitratorPubKeyAsHex = offer.getArbitrator().getPubKeyAsHex();

        CreateDepositTx.run(this::onResultCreateDepositTx,
                this::onFault,
                walletFacade,
                tradeId,
                collateralAmount,
                takerMultiSigPubKey,
                arbitratorPubKeyAsHex);

        state = State.CreateDepositTx;
    }

    public void onResultCreateDepositTx(String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex)
    {
        log.debug("onResultCreateDepositTx");
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;

        BankAccount bankAccount = user.getBankAccount(trade.getOffer().getBankAccountId());
        String accountId = user.getAccountId();

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
        log.debug("onResultRequestTakerDepositPayment");
        listener.onWaitingForPeerResponse(state);
    }

    public void onRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage message)
    {
        log.debug("onRequestOffererPublishDepositTxMessage");

        // validation
        checkState(state == State.RequestTakerDepositPayment);
        checkNotNull(message);
        String takerPayoutAddress = nonEmptyStringOf(message.getTakerPayoutAddress());
        String peersAccountId = nonEmptyStringOf(message.getAccountId());
        BankAccount peersBankAccount = checkNotNull(message.getBankAccount());
        String takerMessagePubKey = nonEmptyStringOf(message.getTakerMessagePubKey());
        String peersContractAsJson = nonEmptyStringOf(message.getContractAsJson());
        String signedTakerDepositTxAsHex = nonEmptyStringOf(message.getSignedTakerDepositTxAsHex());
        String txConnOutAsHex = nonEmptyStringOf(message.getTxConnOutAsHex());
        String txScriptSigAsHex = nonEmptyStringOf(message.getTxScriptSigAsHex());
        long takerTxOutIndex = nonNegativeLongOf(message.getTakerTxOutIndex());

        // apply new state
        state = State.onRequestOffererPublishDepositTxMessage;
        this.takerPayoutAddress = takerPayoutAddress;
        this.peersAccountId = peersAccountId;
        this.peersBankAccount = peersBankAccount;
        this.takerMessagePubKey = takerMessagePubKey;
        this.peersContractAsJson = peersContractAsJson;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.takerTxOutIndex = takerTxOutIndex;

        // next task
        VerifyTakerAccount.run(this::onResultVerifyTakerAccount, this::onFault, blockChainFacade, peersAccountId, peersBankAccount);
        state = State.VerifyTakerAccount;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onResultVerifyTakerAccount()
    {
        log.debug("onResultVerifyTakerAccount");

        String accountId = user.getAccountId();
        BigInteger tradeAmount = trade.getTradeAmount();
        String messagePubKeyAsHex = user.getMessagePubKeyAsHex();
        BankAccount bankAccount = user.getBankAccount(offer.getBankAccountId());
        ECKey registrationKey = walletFacade.getRegistrationAddressInfo().getKey();

        VerifyAndSignContract.run(this::onResultVerifyAndSignContract,
                this::onFault,
                cryptoFacade,
                accountId,
                tradeAmount,
                takeOfferFeeTxId,
                messagePubKeyAsHex,
                offer,
                peersAccountId,
                bankAccount,
                peersBankAccount,
                takerMessagePubKey,
                peersContractAsJson,
                registrationKey);

        state = State.VerifyAndSignContract;
    }

    public void onResultVerifyAndSignContract(Contract contract, String contractAsJson, String signature)
    {
        log.debug("onResultVerifyAndSignContract");

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

    public void onResultSignAndPublishDepositTx(Transaction transaction)
    {
        log.debug("onResultSignAndPublishDepositTx");

        trade.setDepositTransaction(transaction);
        listener.onDepositTxPublished(transaction.getHashAsString());

        Transaction depositTransaction = trade.getDepositTransaction();
        SendDepositTxIdToTaker.run(this::onResultSendDepositTxIdToTaker, this::onFault, peerAddress, messageFacade, tradeId, depositTransaction);
        state = State.SendDepositTxIdToTaker;
    }

    public void onResultSendDepositTxIdToTaker()
    {
        log.debug("onResultSendDepositTxIdToTaker");

        SetupListenerForBlockChainConfirmation.run(this::onResultSetupListenerForBlockChainConfirmation, this::onFault, trade.getDepositTransaction(), listener);
        state = State.SetupListenerForBlockChainConfirmation;
    }

    public void onResultSetupListenerForBlockChainConfirmation()
    {
        log.debug("onResultSetupListenerForBlockChainConfirmation");

        state = State.onResultSetupListenerForBlockChainConfirmation;
        listener.onWaitingForUserInteraction(state);
    }

    // Triggered from UI event: Button click "Bank transfer inited"
    public void onUIEventBankTransferInited()
    {
        log.debug("onUIEventBankTransferInited");

        // validation
        checkState(state == State.onResultSetupListenerForBlockChainConfirmation);

        state = State.onUIEventBankTransferInited;

        // next task
        String depositTransactionId = trade.getDepositTransaction().getHashAsString();
        String offererPayoutAddress = walletFacade.getAddressInfoByTradeID(tradeId).getAddressString();
        BigInteger collateral = trade.getCollateralAmount();
        BigInteger tradeAmount = trade.getTradeAmount();
        SendSignedPayoutTx.run(this::onResultSendSignedPayoutTx, this::onFault, peerAddress, messageFacade, walletFacade, tradeId, takerPayoutAddress, offererPayoutAddress, depositTransactionId, collateral, tradeAmount);
        state = State.SendSignedPayoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onResultSendSignedPayoutTx()
    {
        log.debug("onResultSendSignedPayoutTx");

        listener.onWaitingForPeerResponse(state);
    }

    public void onPayoutTxPublishedMessage(PayoutTxPublishedMessage tradeMessage)
    {
        log.debug("onPayoutTxPublishedMessage");

        // validation
        checkState(state == State.SendSignedPayoutTx);
        String payoutTxAsHex = nonEmptyStringOf(tradeMessage.getPayoutTxAsHex());

        state = State.onPayoutTxPublishedMessage;

        // next task
        listener.onPayoutTxPublished(payoutTxAsHex);
        listener.onCompleted(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return id;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // generic fault handler
    private void onFault(Throwable throwable)
    {
        listener.onFault(throwable, state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

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

}
