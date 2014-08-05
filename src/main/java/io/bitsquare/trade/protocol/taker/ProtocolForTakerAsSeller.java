package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
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
import java.security.PublicKey;
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
        onBankTransferInitedMessage,
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
    private final Offer offer;
    private final String tradeId;
    private final BankAccount bankAccount;
    private final String accountId;
    private final PublicKey messagePublicKey;
    private final Coin tradeAmount;
    private final String pubKeyForThatTrade;
    private final ECKey accountKey;
    private final PublicKey peersMessagePublicKey;
    private final Coin collateral;
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
    private Coin offererPaybackAmount;
    private Coin takerPaybackAmount;
    private String offererPayoutAddress;


    // state
    private State state;
    private int step = 0;


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

        peersMessagePublicKey = offer.getMessagePublicKey();

        bankAccount = user.getBankAccount(offer.getBankAccountId());
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        pubKeyForThatTrade = walletFacade.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
        accountKey = walletFacade.getRegistrationAddressInfo().getKey();

        state = State.Init;
    }

    public void start()
    {
        log.debug("start called " + step++);
        state = State.GetPeerAddress;
        GetPeerAddress.run(this::onResultGetPeerAddress, this::onFault, messageFacade, peersMessagePublicKey);
    }

    public void onResultGetPeerAddress(PeerAddress peerAddress)
    {
        log.debug("onResultGetPeerAddress called " + step++);
        this.peerAddress = peerAddress;

        state = State.RequestTakeOffer;
        RequestTakeOffer.run(this::onResultRequestTakeOffer, this::onFault, peerAddress, messageFacade, tradeId);
    }

    public void onResultRequestTakeOffer()
    {
        log.debug("onResultRequestTakeOffer called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRespondToTakeOfferRequestMessage(RespondToTakeOfferRequestMessage message)
    {
        log.debug("onRespondToTakeOfferRequestMessage called " + step++);
        log.debug("state " + state);
        checkState(state == State.RequestTakeOffer);
        checkArgument(tradeId.equals(message.getTradeId()));

        if (message.isTakeOfferRequestAccepted())
        {
            state = State.PayTakeOfferFee;
            PayTakeOfferFee.run(this::onResultPayTakeOfferFee, this::onFault, walletFacade, tradeId);
        }
        else
        {
            listener.onTakeOfferRequestRejected(trade);
            // exit case
        }
    }

    public void onResultPayTakeOfferFee(String takeOfferFeeTxId)
    {
        log.debug("onResultPayTakeOfferFee called " + step++);
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);

        state = State.SendTakeOfferFeePayedTxId;
        SendTakeOfferFeePayedTxId.run(this::onResultSendTakeOfferFeePayedTxId, this::onFault, peerAddress, messageFacade, tradeId, takeOfferFeeTxId, tradeAmount, pubKeyForThatTrade);
    }

    public void onResultSendTakeOfferFeePayedTxId()
    {
        log.debug("onResultSendTakeOfferFeePayedTxId called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestTakerDepositPaymentMessage(RequestTakerDepositPaymentMessage message)
    {
        log.debug("onRequestTakerDepositPaymentMessage called " + step++);
        log.debug("state " + state);

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
        state = State.VerifyOffererAccount;
        VerifyOffererAccount.run(this::onResultVerifyOffererAccount, this::onFault, blockChainFacade, peersAccountId, peersBankAccount);
    }

    public void onResultVerifyOffererAccount()
    {
        log.debug("onResultVerifyOffererAccount called " + step++);
        String takeOfferFeeTxId = trade.getTakeOfferFeeTxId();
        state = State.CreateAndSignContract;
        CreateAndSignContract.run(this::onResultCreateAndSignContract,
                                  this::onFault,
                                  cryptoFacade,
                                  offer,
                                  tradeAmount,
                                  takeOfferFeeTxId,
                                  accountId,
                                  bankAccount,
                                  peersMessagePublicKey,
                                  messagePublicKey,
                                  peersAccountId,
                                  peersBankAccount,
                                  accountKey);
    }

    public void onResultCreateAndSignContract(Contract contract, String contractAsJson, String signature)
    {
        log.debug("onResultCreateAndSignContract called " + step++);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        state = State.PayDeposit;
        PayDeposit.run(this::onResultPayDeposit, this::onFault, walletFacade, collateral, tradeAmount, tradeId, pubKeyForThatTrade, arbitratorPubKey, peersPubKey, preparedPeersDepositTxAsHex);
    }

    public void onResultPayDeposit(Transaction signedTakerDepositTx)
    {
        log.debug("onResultPayDeposit called " + step++);
        String contractAsJson = trade.getContractAsJson();
        String takerSignature = trade.getTakerSignature();

        state = State.SendSignedTakerDepositTxAsHex;
        SendSignedTakerDepositTxAsHex.run(this::onResultSendSignedTakerDepositTxAsHex,
                                          this::onFault,
                                          peerAddress,
                                          messageFacade,
                                          walletFacade,
                                          bankAccount,
                                          accountId,
                                          messagePublicKey,
                                          tradeId,
                                          contractAsJson,
                                          takerSignature,
                                          signedTakerDepositTx,
                                          peersTxOutIndex);
    }

    public void onResultSendSignedTakerDepositTxAsHex()
    {
        log.debug("onResultSendSignedTakerDepositTxAsHex called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, does only trigger UI feedback/update
    public void onDepositTxPublishedMessage(DepositTxPublishedMessage message)
    {
        log.debug("onDepositTxPublishedMessage called " + step++);
        log.debug("state " + state);
        checkState(state.ordinal() >= State.SendSignedTakerDepositTxAsHex.ordinal());
        checkArgument(tradeId.equals(message.getTradeId()));
        listener.onDepositTxPublished(walletFacade.takerCommitDepositTx(message.getDepositTxAsHex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, store data for later, does only trigger UI feedback/update
    public void onBankTransferInitedMessage(BankTransferInitedMessage message)
    {
        log.debug("onBankTransferInitedMessage called " + step++);
        log.debug("state " + state);
        // validate
        checkState(state.ordinal() >= State.SendSignedTakerDepositTxAsHex.ordinal() && state.ordinal() < State.SignAndPublishPayoutTx.ordinal());
        checkArgument(tradeId.equals(message.getTradeId()));
        String depositTxAsHex = nonEmptyStringOf(message.getDepositTxAsHex());
        String offererSignatureR = nonEmptyStringOf(message.getOffererSignatureR());
        String offererSignatureS = nonEmptyStringOf(message.getOffererSignatureS());
        Coin offererPaybackAmount = positiveCoinOf(nonZeroCoinOf(message.getOffererPaybackAmount()));
        Coin takerPaybackAmount = positiveCoinOf(nonZeroCoinOf(message.getTakerPaybackAmount()));
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
        log.debug("onUIEventFiatReceived called " + step++);
        log.debug("state " + state);
        checkState(state == State.onBankTransferInitedMessage);

        state = State.SignAndPublishPayoutTx;
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
    }

    public void onResultSignAndPublishPayoutTx(String transactionId, String payoutTxAsHex)
    {
        log.debug("onResultSignAndPublishPayoutTx called " + step++);
        listener.onPayoutTxPublished(trade, transactionId);

        state = State.SendPayoutTxToOfferer;
        SendPayoutTxToOfferer.run(this::onResultSendPayoutTxToOfferer, this::onFault, peerAddress, messageFacade, tradeId, payoutTxAsHex);
    }

    public void onResultSendPayoutTxToOfferer()
    {
        log.debug("onResultSendPayoutTxToOfferer called " + step++);
        listener.onCompleted(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return tradeId;
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
