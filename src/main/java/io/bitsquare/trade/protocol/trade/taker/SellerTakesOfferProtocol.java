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
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.msg.MessageService;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayDeposit;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayTakeOfferFee;
import io.bitsquare.trade.protocol.trade.taker.tasks.RequestTakeOffer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTxAsHex;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendTakeOfferFeePayedTxId;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
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
    private final SellerTakesOfferProtocolListener listener;
    private final MessageService messageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final CryptoService cryptoService;

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
    private int step = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerTakesOfferProtocol(Trade trade,
                                    SellerTakesOfferProtocolListener listener,
                                    MessageService messageService,
                                    WalletService walletService,
                                    BlockChainService blockChainService,
                                    CryptoService cryptoService,
                                    User user) {
        this.trade = trade;
        this.listener = listener;
        this.messageService = messageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.cryptoService = cryptoService;

        offer = trade.getOffer();
        tradeId = trade.getId();
        tradeAmount = trade.getTradeAmount();
        securityDeposit = trade.getSecurityDeposit();
        //TODO use 1. for now
        arbitratorPubKey = trade.getOffer().getArbitrators().get(0).getPubKeyAsHex();

        peersMessagePublicKey = offer.getMessagePublicKey();

        bankAccount = user.getCurrentBankAccount();
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        pubKeyForThatTrade = walletService.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
        accountKey = walletService.getRegistrationAddressEntry().getKey();

        state = State.Init;
    }

    public void start() {
        log.debug("start called " + step++);
        state = State.GetPeerAddress;
        GetPeerAddress.run(this::onResultGetPeerAddress, this::onFault, messageService, peersMessagePublicKey);
    }

    public void onResultGetPeerAddress(Peer peer) {
        log.debug("onResultGetPeerAddress called " + step++);
        this.peer = peer;

        state = State.RequestTakeOffer;
        RequestTakeOffer.run(this::onResultRequestTakeOffer, this::onFault, peer, messageService, tradeId);
    }

    public void onResultRequestTakeOffer() {
        log.debug("onResultRequestTakeOffer called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRespondToTakeOfferRequestMessage(RespondToTakeOfferRequestMessage message) {
        log.debug("onRespondToTakeOfferRequestMessage called " + step++);
        log.debug("state " + state);
        checkState(state == State.RequestTakeOffer);
        checkArgument(tradeId.equals(message.getTradeId()));

        if (message.isTakeOfferRequestAccepted()) {
            state = State.PayTakeOfferFee;
            listener.onTakeOfferRequestAccepted(trade);
            PayTakeOfferFee.run(this::onResultPayTakeOfferFee, this::onFault, walletService, tradeId);
        }
        else {
            listener.onTakeOfferRequestRejected(trade);
            // exit case
        }
    }

    public void onResultPayTakeOfferFee(String takeOfferFeeTxId) {
        log.debug("onResultPayTakeOfferFee called " + step++);
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);

        state = State.SendTakeOfferFeePayedTxId;
        SendTakeOfferFeePayedTxId.run(this::onResultSendTakeOfferFeePayedTxId, this::onFault, peer,
                messageService, tradeId, takeOfferFeeTxId, tradeAmount, pubKeyForThatTrade);
    }

    public void onResultSendTakeOfferFeePayedTxId() {
        log.debug("onResultSendTakeOfferFeePayedTxId called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestTakerDepositPaymentMessage(RequestTakerDepositPaymentMessage message) {
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
        VerifyOffererAccount.run(this::onResultVerifyOffererAccount, this::onFault, blockChainService, peersAccountId,
                peersBankAccount);
    }

    public void onResultVerifyOffererAccount() {
        log.debug("onResultVerifyOffererAccount called " + step++);
        String takeOfferFeeTxId = trade.getTakeOfferFeeTxId();
        state = State.CreateAndSignContract;
        CreateAndSignContract.run(this::onResultCreateAndSignContract,
                this::onFault,
                cryptoService,
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

    public void onResultCreateAndSignContract(Contract contract, String contractAsJson, String signature) {
        log.debug("onResultCreateAndSignContract called " + step++);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        state = State.PayDeposit;
        PayDeposit.run(this::onResultPayDeposit, this::onFault, walletService, securityDeposit, tradeAmount, tradeId,
                pubKeyForThatTrade, arbitratorPubKey, peersPubKey, preparedPeersDepositTxAsHex);
    }

    public void onResultPayDeposit(Transaction signedTakerDepositTx) {
        log.debug("onResultPayDeposit called " + step++);
        String contractAsJson = trade.getContractAsJson();
        String takerSignature = trade.getTakerSignature();

        state = State.SendSignedTakerDepositTxAsHex;
        SendSignedTakerDepositTxAsHex.run(this::onResultSendSignedTakerDepositTxAsHex,
                this::onFault,
                peer,
                messageService,
                walletService,
                bankAccount,
                accountId,
                messagePublicKey,
                tradeId,
                contractAsJson,
                takerSignature,
                signedTakerDepositTx,
                peersTxOutIndex);
    }

    public void onResultSendSignedTakerDepositTxAsHex() {
        log.debug("onResultSendSignedTakerDepositTxAsHex called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, does only trigger UI feedback/update
    public void onDepositTxPublishedMessage(DepositTxPublishedMessage message) {
        log.debug("onDepositTxPublishedMessage called " + step++);
        log.debug("state " + state);
        checkState(state.ordinal() >= State.SendSignedTakerDepositTxAsHex.ordinal());
        checkArgument(tradeId.equals(message.getTradeId()));
        //TODO takerCommitDepositTx should be in task as well
        Transaction tx = walletService.takerCommitDepositTx(message.getDepositTxAsHex());
        listener.onDepositTxPublished(tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // informational, store data for later, does only trigger UI feedback/update
    public void onBankTransferInitedMessage(BankTransferInitedMessage message) {
        log.debug("onBankTransferInitedMessage called " + step++);
        log.debug("state " + state);
        // validate
        checkState(state.ordinal() >= State.SendSignedTakerDepositTxAsHex.ordinal() &&
                state.ordinal() < State.SignAndPublishPayoutTx.ordinal());
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
    public void onUIEventFiatReceived() {
        log.debug("onUIEventFiatReceived called " + step++);
        log.debug("state " + state);
        checkState(state == State.onBankTransferInitedMessage);

        state = State.SignAndPublishPayoutTx;
        SignAndPublishPayoutTx.run(this::onResultSignAndPublishPayoutTx,
                this::onFault,
                walletService,
                tradeId,
                depositTxAsHex,
                offererSignatureR,
                offererSignatureS,
                offererPaybackAmount,
                takerPaybackAmount,
                offererPayoutAddress);
    }

    public void onResultSignAndPublishPayoutTx(Transaction transaction, String payoutTxAsHex) {
        log.debug("onResultSignAndPublishPayoutTx called " + step++);
        listener.onPayoutTxPublished(trade, transaction);

        state = State.SendPayoutTxToOfferer;
        SendPayoutTxToOfferer.run(this::onResultSendPayoutTxToOfferer, this::onFault, peer, messageService,
                tradeId, payoutTxAsHex);
    }

    public void onResultSendPayoutTxToOfferer() {
        log.debug("onResultSendPayoutTxToOfferer called " + step++);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return tradeId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // generic fault handler
    private void onFault(Throwable throwable) {
        listener.onFault(throwable, state);
    }

}
