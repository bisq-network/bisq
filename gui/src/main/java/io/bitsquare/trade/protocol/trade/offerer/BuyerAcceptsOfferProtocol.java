/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.BuyerAcceptsOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RespondToTakeOfferRequest;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxIdToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendSignedPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendTakerDepositPaymentRequest;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SetupListenerForBlockChainConfirmation;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import java.security.PublicKey;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing
 * from the peer.
 * <p/>
 * This class handles the role of the offerer as the Bitcoin buyer.
 * <p/>
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages need to be validated before further processing.
 */
public class BuyerAcceptsOfferProtocol {

    private static final Logger log = LoggerFactory.getLogger(BuyerAcceptsOfferProtocol.class);

    public enum State {
        Init,
        RespondToTakeOfferRequest,

        handleTakeOfferFeePayedMessage,
       /* VerifyTakeOfferFeePayment,*/
        CreateDepositTx,
        RequestTakerDepositPayment,

        handleRequestOffererPublishDepositTxMessage,
        VerifyTakerAccount,
        VerifyAndSignContract,
        SignAndPublishDepositTx,
        SendDepositTxIdToTaker,
        SetupListenerForBlockChainConfirmation,

        handleUIEventBankTransferInited,
        SendSignedPayoutTx,

        handlePayoutTxPublishedMessage
    }

    // provided
    private final Trade trade;
    private final Peer peer;
    private final TradeMessageService tradeMessageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final SignatureService signatureService;
    private final BuyerAcceptsOfferProtocolListener listener;

    // derived
    private final String tradeId;
    private final Offer offer;
    private final String arbitratorPubKey;
    private final BankAccount bankAccount;
    private final String accountId;
    private final PublicKey messagePublicKey;
    private final ECKey accountKey;
    private final String payoutAddress;

    // data written/read by tasks
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;

    // data written by messages, read by tasks
    private String takeOfferFeeTxId;
    private String tradePubKeyAsHex;
    private String peersPayoutAddress;
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private PublicKey peersMessagePublicKey;
    private String peersContractAsJson;
    private String signedTakerDepositTxAsHex;
    private String txConnOutAsHex;
    private String txScriptSigAsHex;
    private long takerTxOutIndex;

    // state
    private State state;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAcceptsOfferProtocol(Trade trade,
                                     Peer peer,
                                     TradeMessageService tradeMessageService,
                                     WalletService walletService,
                                     BlockChainService blockChainService,
                                     SignatureService signatureService,
                                     User user,
                                     BuyerAcceptsOfferProtocolListener listener) {
        this.trade = trade;
        this.peer = peer;
        this.listener = listener;
        this.tradeMessageService = tradeMessageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        tradeId = trade.getId();
        offer = trade.getOffer();

        checkNotNull(tradeId);
        checkNotNull(offer);

        //TODO use default arbitrator for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKeyAsHex();

        bankAccount = user.getBankAccount(trade.getOffer().getBankAccountId());
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        accountKey = walletService.getRegistrationAddressEntry().getKey();
        payoutAddress = walletService.getAddressInfoByTradeID(tradeId).getAddressString();

        state = State.Init;
    }

    public void start() {
        respondToTakeOfferRequest();
    }

    // 4. RespondToTakeOfferRequest
    private void respondToTakeOfferRequest() {
        log.debug("respondToTakeOfferRequest called: state = " + state);
        state = State.RespondToTakeOfferRequest;
        RespondToTakeOfferRequest.run(this::handleRespondToTakeOfferRequestResult, this::handleErrorMessage, tradeMessageService, peer,
                trade.getState(), tradeId);
    }

    private void handleRespondToTakeOfferRequestResult() {
        log.debug("handleRespondToTakeOfferRequestResult called: state = " + state);

        // Here we are not setting a state as that is not relevant for the trade process.
        // In accept case we remove the offer from the offerbook, but that happens outside of the flow of the trade process
        if (trade.getState() == Trade.State.OPEN) {
            trade.setState(Trade.State.OFFERER_ACCEPTED);
            listener.onOfferAccepted(offer);
        }
        else {
            log.info("Ignore that request as we have already the offer accepted.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 8. handleTakeOfferFeePayedMessage
    public void handleTakeOfferFeePayedMessage(@NotNull TakeOfferFeePayedMessage message) {
        log.debug("handleTakeOfferFeePayedMessage called: state = " + state);

        // validation
        checkState(state == State.RespondToTakeOfferRequest);
        checkTradeId(tradeId, message);
        String takeOfferFeeTxId = nonEmptyStringOf(message.getTakeOfferFeeTxId());
        Coin tradeAmount = positiveCoinOf(nonZeroCoinOf(message.getTradeAmount()));
        String tradePubKeyAsHex = nonEmptyStringOf(message.getTakerPubKeyAsHex());

        // apply new state
        state = State.handleTakeOfferFeePayedMessage;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.tradePubKeyAsHex = tradePubKeyAsHex;
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);
        trade.setTradeAmount(tradeAmount);

        // next task
        createDepositTx();
    }

    // 9. CreateDepositTx
    private void createDepositTx() {
        log.debug("handleVerifyTakeOfferFeePaymentResult called: state = " + state);
        checkState(state == State.handleTakeOfferFeePayedMessage);
        Coin offererInputAmount = trade.getSecurityDeposit().add(FeePolicy.TX_FEE);
        state = State.CreateDepositTx;
        CreateDepositTx.run(this::handleCreateDepositTxResult, this::handleFault, walletService, tradeId, offererInputAmount,
                tradePubKeyAsHex, arbitratorPubKey);
    }

    // 4. RequestTakerDepositPayment
    private void handleCreateDepositTxResult(String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex) {
        log.debug("handleCreateDepositTxResult called: state = " + state);
        checkState(state == State.CreateDepositTx);

        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;

        state = State.RequestTakerDepositPayment;
        SendTakerDepositPaymentRequest.run(this::handleErrorMessage,
                peer,
                tradeMessageService,
                tradeId,
                bankAccount,
                accountId,
                offererPubKey,
                preparedOffererDepositTxAsHex,
                offererTxOutIndex);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 5. VerifyTakerAccount
    public void handleRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage message) {
        log.debug("handleRequestOffererPublishDepositTxMessage called: state = " + state);
        log.debug("state " + state);

        // validation
        checkState(state == State.RequestTakerDepositPayment);
        checkTradeId(tradeId, message);
        String peersPayoutAddress = nonEmptyStringOf(message.getTakerPayoutAddress());
        String peersAccountId = nonEmptyStringOf(message.getTakerAccountId());
        BankAccount peersBankAccount = checkNotNull(message.getTakerBankAccount());
        PublicKey peersMessagePublicKey = checkNotNull(message.getTakerMessagePublicKey());
        String peersContractAsJson = nonEmptyStringOf(message.getTakerContractAsJson());
        String signedTakerDepositTxAsHex = nonEmptyStringOf(message.getSignedTakerDepositTxAsHex());
        String txConnOutAsHex = nonEmptyStringOf(message.getTxConnOutAsHex());
        String txScriptSigAsHex = nonEmptyStringOf(message.getTxScriptSigAsHex());
        long takerTxOutIndex = nonNegativeLongOf(message.getTakerTxOutIndex());

        // apply new state
        state = State.handleRequestOffererPublishDepositTxMessage;
        this.peersPayoutAddress = peersPayoutAddress;
        this.peersAccountId = peersAccountId;
        this.peersBankAccount = peersBankAccount;
        this.peersMessagePublicKey = peersMessagePublicKey;
        this.peersContractAsJson = peersContractAsJson;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.takerTxOutIndex = takerTxOutIndex;

        // next task
        state = State.VerifyTakerAccount;
        VerifyTakerAccount.run(this::handleVerifyTakerAccountResult, this::handleFault, blockChainService,
                this.peersAccountId, this.peersBankAccount);
    }

    // 6. VerifyAndSignContract
    private void handleVerifyTakerAccountResult() {
        log.debug("handleVerifyTakerAccountResult called: state = " + state);

        Coin tradeAmount = trade.getTradeAmount();
        state = State.VerifyAndSignContract;
        VerifyAndSignContract.run(this::handleVerifyAndSignContractResult,
                this::handleFault,
                signatureService,
                accountId,
                tradeAmount,
                takeOfferFeeTxId,
                messagePublicKey,
                offer,
                peersAccountId,
                bankAccount,
                peersBankAccount,
                peersMessagePublicKey,
                peersContractAsJson,
                accountKey);
    }

    // 7. SignAndPublishDepositTx
    private void handleVerifyAndSignContractResult(Contract contract, String contractAsJson, String signature) {
        log.debug("handleVerifyAndSignContractResult called: state = " + state);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setTakerContractSignature(signature);
        state = State.SignAndPublishDepositTx;
        SignAndPublishDepositTx.run(this::handleSignAndPublishDepositTxResult,
                this::handleFault,
                walletService,
                preparedOffererDepositTxAsHex,
                signedTakerDepositTxAsHex,
                txConnOutAsHex,
                txScriptSigAsHex,
                offererTxOutIndex,
                takerTxOutIndex);
    }

    // 8. SendDepositTxIdToTaker
    private void handleSignAndPublishDepositTxResult(Transaction depositTransaction) {
        log.debug("handleSignAndPublishDepositTxResult called: state = " + state);

        listener.onDepositTxPublished(depositTransaction);

        state = State.SendDepositTxIdToTaker;
        SendDepositTxIdToTaker.run(this::handleSendDepositTxIdToTakerResult, this::handleErrorMessage, peer, tradeMessageService,
                tradeId, depositTransaction);
    }

    private void handleSendDepositTxIdToTakerResult() {
        log.debug("handleSendDepositTxIdToTakerResult called: state = " + state);

        state = State.SetupListenerForBlockChainConfirmation;
        SetupListenerForBlockChainConfirmation.run(trade.getDepositTx(), listener);
    }

    /*
     // 9. VerifyTakeOfferFeePayment
    private void verifyTakeOfferFeePayment() {
        state = State.VerifyTakeOfferFeePayment;
        VerifyTakeOfferFeePayment.run(this::handleVerifyTakeOfferFeePaymentResult, this::handleFault, walletService, this.takeOfferFeeTxId);
    }

    // 10. CreateDepositTx
    private void handleVerifyTakeOfferFeePaymentResult() {
        log.debug("handleVerifyTakeOfferFeePaymentResult called: state = " + state);
        checkState(state == State.VerifyTakeOfferFeePayment);
        Coin offererInputAmount = trade.getSecurityDeposit().add(FeePolicy.TX_FEE);
        state = State.CreateDepositTx;
        CreateDepositTx.run(this::handleCreateDepositTxResult, this::handleFault, walletService, tradeId, offererInputAmount,
                tradePubKeyAsHex, arbitratorPubKey);
    }
     */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Triggered from UI event: Button click "Bank transfer inited"
    // 9. SendSignedPayoutTx
    public void handleUIEventBankTransferInited() {
        log.debug("onUIEventBankTransferInited called: state = " + state);
        log.debug("state " + state);

        // validation
        checkState(state.ordinal() >= State.SignAndPublishDepositTx.ordinal() &&
                state.ordinal() <= State.SetupListenerForBlockChainConfirmation.ordinal());

        state = State.handleUIEventBankTransferInited;

        // next task
        String depositTransactionId = trade.getDepositTx().getHashAsString();
        Coin tradeAmount = trade.getTradeAmount();
        Coin securityDeposit = trade.getSecurityDeposit();
        state = State.SendSignedPayoutTx;
        SendSignedPayoutTx.run(this::handleFault,
                peer,
                tradeMessageService,
                walletService,
                tradeId,
                peersPayoutAddress,
                payoutAddress,
                depositTransactionId,
                securityDeposit,
                tradeAmount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 10. handlePayoutTxPublishedMessage
    public void handlePayoutTxPublishedMessage(PayoutTxPublishedMessage message) {
        log.debug("onPayoutTxPublishedMessage called: state = " + state);

        // validation
        checkState(state == State.SendSignedPayoutTx);
        checkTradeId(tradeId, message);
        String payoutTxAsHex = nonEmptyStringOf(message.getPayoutTxAsHex());

        // apply new state
        state = State.handlePayoutTxPublishedMessage;
        Transaction payoutTx = new Transaction(walletService.getWallet().getParams(), Utils.parseAsHexOrBase58(payoutTxAsHex));
        listener.onPayoutTxPublished(payoutTx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // generic fault handler
    private void handleFault(Throwable throwable) {
        trade.setFault(throwable);
        trade.setState(Trade.State.FAILED);
        listener.onFault(throwable, state);
    }

    private void handleErrorMessage(String errorMessage) {
        handleFault(new Exception(errorMessage));
    }

}
