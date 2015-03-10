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

package io.bitsquare.trade.protocol.trade.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.SellerTakesOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.TakerDepositPaymentRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayDeposit;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayTakeOfferFee;
import io.bitsquare.trade.protocol.trade.taker.tasks.RequestTakeOffer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTxAsHex;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendTakeOfferFeePayedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;


/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing
 * from the peer.
 * That class handles the role of the taker as the Bitcoin seller.
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages as well data used to send to the peer need to be validated before further processing.
 */
public class SellerTakesOfferProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerTakesOfferProtocol.class);


    public enum State {
        Init,
        GetPeerAddress,
        RequestTakeOffer,

        ValidateRespondToTakeOfferRequestMessage,
        PayTakeOfferFee,
        SendTakeOfferFeePayedMessage,

        ValidateTakerDepositPaymentRequestMessage,
        VerifyOffererAccount,
        CreateAndSignContract,
        PayDeposit,
        SendSignedTakerDepositTxAsHex,

        ValidateDepositTxPublishedMessage,
        TakerCommitDepositTx,
        handleBankTransferInitedMessage,
        SignAndPublishPayoutTx,
        SendPayoutTxToOfferer
    }

    // provided data
    private final Trade trade;
    private final SellerTakesOfferProtocolListener listener;
    private final TradeMessageService tradeMessageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final SignatureService signatureService;

    // derived
    private final Offer offer;
    private final String tradeId;
    private final BankAccount bankAccount;
    private final String accountId;
    private final PublicKey messagePublicKey;
    private final Coin tradeAmount;
    private final String tradePubKeyAsHex;
    private final ECKey accountKey;
    private final PublicKey offererMessagePublicKey;
    private final Coin securityDeposit;
    private final String arbitratorPubKey;

    // written/read by task
    private Peer peer;

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerTakesOfferProtocol(Trade trade,
                                    SellerTakesOfferProtocolListener listener,
                                    TradeMessageService tradeMessageService,
                                    WalletService walletService,
                                    BlockChainService blockChainService,
                                    SignatureService signatureService,
                                    User user) {
        this.trade = trade;
        this.listener = listener;
        this.tradeMessageService = tradeMessageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        offer = trade.getOffer();
        tradeId = trade.getId();
        tradeAmount = trade.getTradeAmount();
        securityDeposit = trade.getSecurityDeposit();
        //TODO use 1. for now
        arbitratorPubKey = trade.getOffer().getArbitrators().get(0).getPubKeyAsHex();

        offererMessagePublicKey = offer.getMessagePublicKey();

        bankAccount = user.getCurrentBankAccount().get();
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        tradePubKeyAsHex = walletService.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
        accountKey = walletService.getRegistrationAddressEntry().getKey();

        state = State.Init;
    }

    public void start() {
        getPeerAddress();
    }

    // 1. GetPeerAddress
    private void getPeerAddress() {
        log.debug("getPeerAddress called: state = " + state);
        state = State.GetPeerAddress;
        GetPeerAddress.run(this::handleGetPeerAddressResult, this::handleErrorMessage, tradeMessageService, offererMessagePublicKey);
    }

    // 2. RequestTakeOffer
    private void handleGetPeerAddressResult(Peer peer) {
        log.debug("handleGetPeerAddressResult called: state = " + state);
        this.peer = peer;
        state = State.RequestTakeOffer;
        RequestTakeOffer.run(this::handleErrorMessage, tradeMessageService, peer, tradeId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 5. handleRespondToTakeOfferRequestMessage
    public void handleRespondToTakeOfferRequestMessage(RespondToTakeOfferRequestMessage message) {
        log.debug("handleRespondToTakeOfferRequestMessage called: state = " + state);

        try {
            // validation
            checkState(state == State.RequestTakeOffer);
            state = State.ValidateRespondToTakeOfferRequestMessage;
            checkTradeId(tradeId, message);

            // apply new state
            if (message.isTakeOfferRequestAccepted()) {
                trade.setState(Trade.State.OFFERER_ACCEPTED);
                listener.onTakeOfferRequestAccepted();

                // next task
                payTakeOfferFee();
            }
            else {
                // exit case
                trade.setState(Trade.State.OFFERER_REJECTED);
                listener.onTakeOfferRequestRejected();
            }
        } catch (Throwable t) {
            handleValidationFault(t);
        }
    }

    // 6. PayTakeOfferFee
    private void payTakeOfferFee() {
        state = State.PayTakeOfferFee;
        PayTakeOfferFee.run(this::handlePayTakeOfferFeeResult, this::handleFault, walletService, tradeId);
    }

    // 7. SendTakeOfferFeePayedMessage
    private void handlePayTakeOfferFeeResult(String takeOfferFeeTxId) {
        log.debug("handlePayTakeOfferFeeResult called: state = " + state);
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);
        state = State.SendTakeOfferFeePayedMessage;
        SendTakeOfferFeePayedMessage.run(this::handleErrorMessage, peer, tradeMessageService, tradeId, takeOfferFeeTxId, tradeAmount, tradePubKeyAsHex);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 11. VerifyOffererAccount
    public void handleTakerDepositPaymentRequestMessage(TakerDepositPaymentRequestMessage message) {
        log.debug("handleTakerDepositPaymentRequestMessage called: state = " + state);

        try {
            // validation
            checkState(state == State.SendTakeOfferFeePayedMessage);
            state = State.ValidateTakerDepositPaymentRequestMessage;
            checkTradeId(tradeId, message);
            String peersAccountId = nonEmptyStringOf(message.getAccountId());
            BankAccount peersBankAccount = checkNotNull(message.getBankAccount());
            String offererPubKey = nonEmptyStringOf(message.getOffererPubKey());
            String preparedOffererDepositTxAsHex = nonEmptyStringOf(message.getPreparedOffererDepositTxAsHex());
            long offererTxOutIndex = nonNegativeLongOf(message.getOffererTxOutIndex());

            // apply new state
            this.peersAccountId = peersAccountId;
            this.peersBankAccount = peersBankAccount;
            this.peersPubKey = offererPubKey;
            this.preparedPeersDepositTxAsHex = preparedOffererDepositTxAsHex;
            this.peersTxOutIndex = offererTxOutIndex;

            // next task
            verifyOffererAccount();
        } catch (Throwable t) {
            handleValidationFault(t);
        }
    }

    // 12. VerifyOffererAccount
    private void verifyOffererAccount() {
        state = State.VerifyOffererAccount;
        VerifyOffererAccount.run(this::handleVerifyOffererAccountResult, this::handleFault, blockChainService, peersAccountId, peersBankAccount);
    }

    // 13. CreateAndSignContract
    private void handleVerifyOffererAccountResult() {
        log.debug("handleVerifyOffererAccountResult called: state = " + state);
        String takeOfferFeeTxId = trade.getTakeOfferFeeTxId();
        state = State.CreateAndSignContract;
        CreateAndSignContract.run(this::handleCreateAndSignContractResult,
                this::handleFault,
                signatureService,
                offer,
                tradeAmount,
                takeOfferFeeTxId,
                accountId,
                bankAccount,
                offererMessagePublicKey,
                messagePublicKey,
                peersAccountId,
                peersBankAccount,
                accountKey);
    }

    // 14. PayDeposit
    private void handleCreateAndSignContractResult(Contract contract, String contractAsJson, String signature) {
        log.debug("handleCreateAndSignContractResult called: state = " + state);
        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setTakerContractSignature(signature);
        state = State.PayDeposit;
        PayDeposit.run(this::handlePayDepositResult, this::handleFault, walletService, securityDeposit, tradeAmount, tradeId,
                tradePubKeyAsHex, arbitratorPubKey, peersPubKey, preparedPeersDepositTxAsHex);
    }

    // 15. SendSignedTakerDepositTxAsHex
    private void handlePayDepositResult(Transaction signedTakerDepositTx) {
        log.debug("handlePayDepositResult called: state = " + state);
        String contractAsJson = trade.getContractAsJson();
        String takerContractSignature = trade.getTakerContractSignature();
        state = State.SendSignedTakerDepositTxAsHex;
        SendSignedTakerDepositTxAsHex.run(this::handleErrorMessage,
                peer,
                tradeMessageService,
                walletService,
                bankAccount,
                accountId,
                messagePublicKey,
                tradeId,
                contractAsJson,
                takerContractSignature,
                signedTakerDepositTx,
                peersTxOutIndex);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 21. TakerCommitDepositTx
    public void handleDepositTxPublishedMessage(DepositTxPublishedMessage message) {
        log.debug("onDepositTxPublishedMessage called: state = " + state);
        log.debug("state " + state);

        try {
            // validation
            checkState(state == State.SendSignedTakerDepositTxAsHex);
            state = State.ValidateDepositTxPublishedMessage;
            checkTradeId(tradeId, message);
            String depositTxAsHex = message.getDepositTxAsHex();
            nonEmptyStringOf(depositTxAsHex);

            // next task
            takerCommitDepositTx(depositTxAsHex);
        } catch (Throwable t) {
            handleValidationFault(t);
        }
    }

    // 22. TakerCommitDepositTx
    private void takerCommitDepositTx(String depositTxAsHex) {
        state = State.TakerCommitDepositTx;
        TakerCommitDepositTx.run(this::handleTakerCommitDepositTxResult, this::handleFault, walletService, depositTxAsHex);
    }

    private void handleTakerCommitDepositTxResult(Transaction transaction) {
        log.debug("handleTakerCommitDepositTxResult called: state = " + state);
        trade.setDepositTx(transaction);
        trade.setState(Trade.State.DEPOSIT_PUBLISHED);
        listener.onDepositTxPublished();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 25. handleBankTransferInitedMessage
    public void handleBankTransferInitedMessage(BankTransferInitedMessage message) {
        log.debug("handleBankTransferInitedMessage called: state = " + state);

        try {
            // validate
            checkState(state == State.TakerCommitDepositTx);
            checkTradeId(tradeId, message);
            String depositTxAsHex = nonEmptyStringOf(message.getDepositTxAsHex());
            String offererSignatureR = nonEmptyStringOf(message.getOffererSignatureR());
            String offererSignatureS = nonEmptyStringOf(message.getOffererSignatureS());
            Coin offererPaybackAmount = positiveCoinOf(nonZeroCoinOf(message.getOffererPaybackAmount()));
            Coin takerPaybackAmount = positiveCoinOf(nonZeroCoinOf(message.getTakerPaybackAmount()));
            String offererPayoutAddress = nonEmptyStringOf(message.getOffererPayoutAddress());

            // apply state
            state = State.handleBankTransferInitedMessage;
            this.depositTxAsHex = depositTxAsHex;
            this.offererSignatureR = offererSignatureR;
            this.offererSignatureS = offererSignatureS;
            this.offererPaybackAmount = offererPaybackAmount;
            this.takerPaybackAmount = takerPaybackAmount;
            this.offererPayoutAddress = offererPayoutAddress;

            listener.onBankTransferInited(message.getTradeId());
        } catch (Throwable t) {
            handleValidationFault(t);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    // 26. SignAndPublishPayoutTx
    public void handleUIEventFiatReceived() {
        log.debug("handleUIEventFiatReceived called: state = " + state);
        checkState(state == State.handleBankTransferInitedMessage);

        state = State.SignAndPublishPayoutTx;
        SignAndPublishPayoutTx.run(this::handleSignAndPublishPayoutTxResult,
                this::handleFault,
                walletService,
                tradeId,
                depositTxAsHex,
                offererSignatureR,
                offererSignatureS,
                offererPaybackAmount,
                takerPaybackAmount,
                offererPayoutAddress);

        verifyOfferFeePayment();
    }

    // 27a. SendPayoutTxToOfferer
    private void handleSignAndPublishPayoutTxResult(Transaction transaction, String payoutTxAsHex) {
        log.debug("handleSignAndPublishPayoutTxResult called: state = " + state);
        listener.onPayoutTxPublished(trade, transaction);
        state = State.SendPayoutTxToOfferer;
        SendPayoutTxToOfferer.run(this::handleErrorMessage, peer, tradeMessageService, tradeId, payoutTxAsHex);
    }

    // 27b VerifyTakeOfferFeePayment
    private void verifyOfferFeePayment() {
        VerifyOfferFeePayment.run(this::handleFault, walletService, trade.getOffer().getOfferFeePaymentTxID());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(Throwable throwable) {
        trade.setFault(throwable);
        trade.setState(Trade.State.FAILED);
        listener.onFault(throwable, state);
    }

    private void handleErrorMessage(String errorMessage) {
        handleFault(new Exception(errorMessage));
    }

    private void handleValidationFault(Throwable throwable) {
        throwable.printStackTrace();
        log.error(throwable.getMessage());
        handleErrorMessage("Validation of incoming message failed. Error message = " + throwable.getMessage());
    }
}
