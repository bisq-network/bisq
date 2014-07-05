package io.bitsquare.trade.protocol.taker;

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
import io.bitsquare.trade.protocol.offerer.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.offerer.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.offerer.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.offerer.RespondToTakeOfferRequestMessage;
import io.bitsquare.user.User;
import java.math.BigInteger;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing from the peer.
 * That class handles the role of the taker as the Bitcoin seller.
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages as well data used to send to the peer need to be validated before further processing.
 */
public class ProtocolForTakerAsSeller
{
    private static final Logger log = LoggerFactory.getLogger(ProtocolForTakerAsSeller.class);


    public enum State
    {
        Init,
        GetPeerAddress,
        RequestTakeOffer,
        PayTakeOfferFee,

        onRequestTakerDepositPaymentMessage,

        SendTakeOfferFeePayedTxId,
        VerifyOffererAccount,
        CreateAndSignContract,
        PayDeposit,
        SendSignedTakerDepositTxAsHex,
        onDepositTxPublishedMessage,
        onBankTransferInitedMessage,
        onUIEventFiatReceived,
        SignAndPublishPayoutTx,
        SendPayoutTxToOfferer
    }

    // provided data
    private final Trade trade;
    private final ProtocolForTakerAsSellerListener listener;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final BlockChainFacade blockChainFacade;
    private final CryptoFacade cryptoFacade;

    // derived
    private final String id;
    private final Offer offer;
    private final String tradeId;
    private final BankAccount bankAccount;
    private final String accountId;
    private final String messagePubKey;
    private final BigInteger tradeAmount;
    private final String pubKeyForThatTrade;
    private final ECKey accountKey;
    private final String peersMessagePubKey;
    private final BigInteger collateral;
    private final String arbitratorPubKey;

    // written/read by task
    private PeerAddress peerAddress;

    // written by messages, read by tasks
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private String peersPubKey;
    private String preparedPeersDepositTxAsHex;
    private long peersTxOutIndex;

    private String depositTxAsHex;
    private String offererSignatureR;
    private String offererSignatureS;
    private BigInteger offererPaybackAmount;
    private BigInteger takerPaybackAmount;
    private String offererPayoutAddress;


    // state
    private State state;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProtocolForTakerAsSeller(Trade trade,
                                    ProtocolForTakerAsSellerListener listener,
                                    MessageFacade messageFacade,
                                    WalletFacade walletFacade,
                                    BlockChainFacade blockChainFacade,
                                    CryptoFacade cryptoFacade,
                                    User user)
    {
        this.trade = trade;
        this.listener = listener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;

        offer = trade.getOffer();
        tradeId = trade.getId();
        tradeAmount = trade.getTradeAmount();
        collateral = trade.getCollateralAmount();
        arbitratorPubKey = trade.getOffer().getArbitrator().getPubKeyAsHex();

        pubKeyForThatTrade = walletFacade.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();

        bankAccount = user.getBankAccount(offer.getBankAccountId());
        accountId = user.getAccountId();
        messagePubKey = user.getMessagePubKeyAsHex();

        peersMessagePubKey = offer.getMessagePubKeyAsHex();
        accountKey = walletFacade.getRegistrationAddressInfo().getKey();

        id = trade.getId();

        state = State.Init;
    }

    public void start()
    {
        log.debug("start called");
        GetPeerAddress.run(this::onResultGetPeerAddress, this::onFault, messageFacade, peersMessagePubKey);
        state = State.GetPeerAddress;
    }

    public void onResultGetPeerAddress(PeerAddress peerAddress)
    {
        log.debug(" called");
        this.peerAddress = peerAddress;

        RequestTakeOffer.run(this::onResultRequestTakeOffer, this::onFault, peerAddress, messageFacade, tradeId);
        state = State.RequestTakeOffer;
    }

    public void onResultRequestTakeOffer()
    {
        log.debug(" called");
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRespondToTakeOfferRequestMessage(RespondToTakeOfferRequestMessage message)
    {
        log.debug("onRespondToTakeOfferRequestMessage called");
        checkState(state == State.RequestTakeOffer);
        checkArgument(tradeId.equals(message.getTradeId()));

        if (message.isTakeOfferRequestAccepted())
        {
            PayTakeOfferFee.run(this::onResultPayTakeOfferFee, this::onFault, walletFacade, tradeId);
            state = State.PayTakeOfferFee;
        }
        else
        {
            listener.onTakeOfferRequestRejected(trade);
        }
    }

    public void onResultPayTakeOfferFee(String takeOfferFeeTxId)
    {
        log.debug("onResultPayTakeOfferFee called");
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);

        SendTakeOfferFeePayedTxId.run(this::onResultSendTakeOfferFeePayedTxId, this::onFault, peerAddress, messageFacade, tradeId, takeOfferFeeTxId, tradeAmount, pubKeyForThatTrade);
        state = State.SendTakeOfferFeePayedTxId;
    }

    public void onResultSendTakeOfferFeePayedTxId()
    {
        log.debug("onResultSendTakeOfferFeePayedTxId called");
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestTakerDepositPaymentMessage(RequestTakerDepositPaymentMessage message)
    {
        log.debug("onRequestTakerDepositPaymentMessage called");

        // validation
        checkState(state == State.SendTakeOfferFeePayedTxId);
        checkArgument(tradeId.equals(message.getTradeId()));
        String peersAccountId = nonEmptyStringOf(message.getAccountId());
        BankAccount peersBankAccount = checkNotNull(message.getBankAccount());
        String offererPubKey = nonEmptyStringOf(message.getOffererPubKey());
        String preparedOffererDepositTxAsHex = nonEmptyStringOf(message.getPreparedOffererDepositTxAsHex());
        long offererTxOutIndex = nonNegativeLongOf(message.getOffererTxOutIndex());

        // apply new state
        state = State.onRequestTakerDepositPaymentMessage;
        this.peersAccountId = peersAccountId;
        this.peersBankAccount = peersBankAccount;
        this.peersPubKey = offererPubKey;
        this.preparedPeersDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.peersTxOutIndex = offererTxOutIndex;

        // next task
        VerifyOffererAccount.run(this::onResultVerifyOffererAccount, this::onFault, blockChainFacade, peersAccountId, peersBankAccount);
        state = State.VerifyOffererAccount;
    }

    public void onResultVerifyOffererAccount()
    {
        log.debug("onResultVerifyOffererAccount called");
        String takeOfferFeeTxId = trade.getTakeOfferFeeTxId();
        CreateAndSignContract.run(this::onResultCreateAndSignContract,
                                  this::onFault,
                                  cryptoFacade,
                                  offer,
                                  tradeAmount,
                                  takeOfferFeeTxId,
                                  accountId,
                                  bankAccount,
                                  peersMessagePubKey,
                                  messagePubKey,
                                  peersAccountId,
                                  peersBankAccount,
                                  accountKey);
        state = State.CreateAndSignContract;
    }

    public void onResultCreateAndSignContract(Contract contract, String contractAsJson, String signature)
    {
        log.debug("onResultCreateAndSignContract called");

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        PayDeposit.run(this::onResultPayDeposit, this::onFault, walletFacade, collateral, tradeAmount, tradeId, pubKeyForThatTrade, arbitratorPubKey, peersPubKey, preparedPeersDepositTxAsHex);
        state = State.PayDeposit;
    }

    public void onResultPayDeposit(Transaction signedTakerDepositTx)
    {
        log.debug("onResultPayDeposit called");
        String contractAsJson = trade.getContractAsJson();
        String takerSignature = trade.getTakerSignature();

        SendSignedTakerDepositTxAsHex.run(this::onResultSendSignedTakerDepositTxAsHex,
                                          this::onFault,
                                          peerAddress,
                                          messageFacade,
                                          walletFacade,
                                          bankAccount,
                                          accountId,
                                          messagePubKey,
                                          tradeId,
                                          contractAsJson,
                                          takerSignature,
                                          signedTakerDepositTx,
                                          peersTxOutIndex);
        state = State.SendSignedTakerDepositTxAsHex;
    }

    public void onResultSendSignedTakerDepositTxAsHex()
    {
        log.debug(" called");
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, does only trigger UI feedback/update
    public void onDepositTxPublishedMessage(DepositTxPublishedMessage message)
    {
        log.debug("onDepositTxPublishedMessage called");
        checkState(state.ordinal() > State.SendSignedTakerDepositTxAsHex.ordinal() && state.ordinal() < State.SignAndPublishPayoutTx.ordinal());
        checkArgument(tradeId.equals(message.getTradeId()));
        state = State.onDepositTxPublishedMessage;
        listener.onDepositTxPublished(walletFacade.takerCommitDepositTx(message.getDepositTxAsHex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, store data for later, does only trigger UI feedback/update
    public void onBankTransferInitedMessage(BankTransferInitedMessage message)
    {
        log.debug("onBankTransferInitedMessage called");

        // validate
        checkState(state.ordinal() > State.SendSignedTakerDepositTxAsHex.ordinal() && state.ordinal() < State.SignAndPublishPayoutTx.ordinal());
        checkArgument(tradeId.equals(message.getTradeId()));
        String depositTxAsHex = nonEmptyStringOf(message.getDepositTxAsHex());
        String offererSignatureR = nonEmptyStringOf(message.getOffererSignatureR());
        String offererSignatureS = nonEmptyStringOf(message.getOffererSignatureS());
        BigInteger offererPaybackAmount = nonNegativeBigIntegerOf(nonZeroBigIntegerOf(message.getOffererPaybackAmount()));
        BigInteger takerPaybackAmount = nonNegativeBigIntegerOf(nonZeroBigIntegerOf(message.getTakerPaybackAmount()));
        String offererPayoutAddress = nonEmptyStringOf(message.getOffererPayoutAddress());

        // apply state
        state = State.onBankTransferInitedMessage;
        this.depositTxAsHex = depositTxAsHex;
        this.offererSignatureR = offererSignatureR;
        this.offererSignatureS = offererSignatureS;
        this.offererPaybackAmount = offererPaybackAmount;
        this.takerPaybackAmount = takerPaybackAmount;
        this.offererPayoutAddress = offererPayoutAddress;

        listener.onBankTransferInited(message.getTradeId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onUIEventFiatReceived()
    {
        log.debug("onUIEventFiatReceived called");

        checkState(state.ordinal() > State.SendSignedTakerDepositTxAsHex.ordinal() && state.ordinal() < State.SignAndPublishPayoutTx.ordinal());
        state = State.onUIEventFiatReceived;

        SignAndPublishPayoutTx.run(this::onResultSignAndPublishPayoutTx,
                                   this::onFault,
                                   walletFacade,
                                   tradeId,
                                   depositTxAsHex,
                                   offererSignatureR,
                                   offererSignatureS,
                                   offererPaybackAmount,
                                   takerPaybackAmount,
                                   offererPayoutAddress);
        state = State.SignAndPublishPayoutTx;
    }

    public void onResultSignAndPublishPayoutTx(String transactionId, String payoutTxAsHex)
    {
        log.debug("onResultSignAndPublishPayoutTx called");
        listener.onPayoutTxPublished(trade, transactionId);

        SendPayoutTxToOfferer.run(this::onResultSendPayoutTxToOfferer, this::onFault, peerAddress, messageFacade, tradeId, payoutTxAsHex);
        state = State.SendPayoutTxToOfferer;
    }

    public void onResultSendPayoutTxToOfferer()
    {
        log.debug("onResultSendPayoutTxToOfferer called");
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
