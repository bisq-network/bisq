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
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.HandleTakeOfferRequest;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RequestTakerDepositPayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxIdToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendSignedPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SetupListenerForBlockChainConfirmation;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;

import java.security.PublicKey;

import net.tomp2p.peers.PeerAddress;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing
 * from the peer.
 * <p>
 * This class handles the role of the offerer as the Bitcoin buyer.
 * <p>
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages need to be validated before further processing.
 */
public class BuyerAcceptsOfferProtocol {

    private static final Logger log = LoggerFactory.getLogger(BuyerAcceptsOfferProtocol.class);

    public enum State {
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

    // provided
    private final Trade trade;
    private final PeerAddress peerAddress;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final BlockChainFacade blockChainFacade;
    private final CryptoFacade cryptoFacade;
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
    private String takerPubKey;
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
    private int step = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAcceptsOfferProtocol(Trade trade,
                                     PeerAddress peerAddress,
                                     MessageFacade messageFacade,
                                     WalletFacade walletFacade,
                                     BlockChainFacade blockChainFacade,
                                     CryptoFacade cryptoFacade,
                                     User user,
                                     BuyerAcceptsOfferProtocolListener listener) {
        this.trade = trade;
        this.peerAddress = peerAddress;
        this.listener = listener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;

        tradeId = trade.getId();
        offer = trade.getOffer();

        //TODO use first for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKeyAsHex();

        bankAccount = user.getBankAccount(trade.getOffer().getBankAccountId());
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        accountKey = walletFacade.getRegistrationAddressEntry().getKey();
        payoutAddress = walletFacade.getAddressInfoByTradeID(tradeId).getAddressString();

        state = State.Init;
    }

    public void start() {
        log.debug("start called " + step++);
        state = State.HandleTakeOfferRequest;
        HandleTakeOfferRequest.run(this::onResultHandleTakeOfferRequest, this::onFault, peerAddress, messageFacade,
                trade.getState(), tradeId);
    }

    public void onResultHandleTakeOfferRequest(boolean takeOfferRequestAccepted) {
        log.debug("onResultHandleTakeOfferRequest called " + step++);
        if (takeOfferRequestAccepted) {
            trade.setState(Trade.State.OFFERER_ACCEPTED);
            listener.onOfferAccepted(offer);
            listener.onWaitingForPeerResponse(state);
        }
        else {
            log.info("Finish here as we have already the offer accepted.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTakeOfferFeePayedMessage(@NotNull TakeOfferFeePayedMessage message) {
        log.debug("onTakeOfferFeePayedMessage called " + step++);
        log.debug("state " + state);

        // validation
        checkState(state == State.HandleTakeOfferRequest);
        checkArgument(tradeId.equals(message.getTradeId()));
        String takeOfferFeeTxId = nonEmptyStringOf(message.getTakeOfferFeeTxId());
        Coin tradeAmount = positiveCoinOf(nonZeroCoinOf(message.getTradeAmount()));
        String takerPubKey = nonEmptyStringOf(message.getTakerPubKey());

        // apply new state
        state = State.onTakeOfferFeePayedMessage;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.takerPubKey = takerPubKey;
        trade.setTakeOfferFeeTxID(takeOfferFeeTxId);
        trade.setTradeAmount(tradeAmount);

        // next task
        state = State.VerifyTakeOfferFeePayment;
        VerifyTakeOfferFeePayment.run(this::onResultVerifyTakeOfferFeePayment, this::onFault, walletFacade,
                this.takeOfferFeeTxId);
    }

    public void onResultVerifyTakeOfferFeePayment() {
        log.debug("onResultVerifyTakeOfferFeePayment called " + step++);

        Coin collateral = trade.getCollateralAmount();
        Coin offererInputAmount = collateral.add(FeePolicy.TX_FEE);
        state = State.CreateDepositTx;
        CreateDepositTx.run(this::onResultCreateDepositTx, this::onFault, walletFacade, tradeId, offererInputAmount,
                takerPubKey, arbitratorPubKey);
    }

    public void onResultCreateDepositTx(String offererPubKey, String preparedOffererDepositTxAsHex,
                                        long offererTxOutIndex) {
        log.debug("onResultCreateDepositTx called " + step++);
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;

        state = State.RequestTakerDepositPayment;
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
    }

    public void onResultRequestTakerDepositPayment() {
        log.debug("onResultRequestTakerDepositPayment called " + step++);
        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage message) {
        log.debug("onRequestOffererPublishDepositTxMessage called " + step++);
        log.debug("state " + state);

        // validation
        checkState(state == State.RequestTakerDepositPayment);
        checkArgument(tradeId.equals(message.getTradeId()));
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
        state = State.onRequestOffererPublishDepositTxMessage;
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
        VerifyTakerAccount.run(this::onResultVerifyTakerAccount, this::onFault, blockChainFacade,
                this.peersAccountId, this.peersBankAccount);
    }

    public void onResultVerifyTakerAccount() {
        log.debug("onResultVerifyTakerAccount called " + step++);

        Coin tradeAmount = trade.getTradeAmount();
        state = State.VerifyAndSignContract;
        VerifyAndSignContract.run(this::onResultVerifyAndSignContract,
                this::onFault,
                cryptoFacade,
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

    public void onResultVerifyAndSignContract(Contract contract, String contractAsJson, String signature) {
        log.debug("onResultVerifyAndSignContract called " + step++);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);
        state = State.SignAndPublishDepositTx;
        SignAndPublishDepositTx.run(this::onResultSignAndPublishDepositTx,
                this::onFault,
                walletFacade,
                preparedOffererDepositTxAsHex,
                signedTakerDepositTxAsHex,
                txConnOutAsHex,
                txScriptSigAsHex,
                offererTxOutIndex,
                takerTxOutIndex);
    }

    public void onResultSignAndPublishDepositTx(Transaction depositTransaction) {
        log.debug("onResultSignAndPublishDepositTx called " + step++);

        listener.onDepositTxPublished(depositTransaction);

        state = State.SendDepositTxIdToTaker;
        SendDepositTxIdToTaker.run(this::onResultSendDepositTxIdToTaker, this::onFault, peerAddress, messageFacade,
                tradeId, depositTransaction);
    }

    public void onResultSendDepositTxIdToTaker() {
        log.debug("onResultSendDepositTxIdToTaker called " + step++);

        state = State.SetupListenerForBlockChainConfirmation;
        SetupListenerForBlockChainConfirmation.run(this::onResultSetupListenerForBlockChainConfirmation,
                trade.getDepositTx(), listener);
    }

    public void onResultSetupListenerForBlockChainConfirmation() {
        log.debug("onResultSetupListenerForBlockChainConfirmation called " + step++);

        state = State.onResultSetupListenerForBlockChainConfirmation;
        listener.onWaitingForUserInteraction(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Triggered UI event
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Triggered from UI event: Button click "Bank transfer inited"
    public void onUIEventBankTransferInited() {
        log.debug("onUIEventBankTransferInited called " + step++);
        log.debug("state " + state);

        // validation
        checkState(state.ordinal() >= State.SignAndPublishDepositTx.ordinal() &&
                state.ordinal() <= State.onResultSetupListenerForBlockChainConfirmation.ordinal());

        state = State.onUIEventBankTransferInited;

        // next task
        String depositTransactionId = trade.getDepositTx().getHashAsString();
        Coin tradeAmount = trade.getTradeAmount();
        Coin collateral = trade.getCollateralAmount();
        state = State.SendSignedPayoutTx;
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
    }

    public void onResultSendSignedPayoutTx() {
        log.debug("onResultSendSignedPayoutTx called " + step++);

        listener.onWaitingForPeerResponse(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPayoutTxPublishedMessage(PayoutTxPublishedMessage message) {
        log.debug("onPayoutTxPublishedMessage called " + step++);
        log.debug("state " + state);

        // validation
        checkState(state == State.SendSignedPayoutTx);
        checkArgument(tradeId.equals(message.getTradeId()));
        String payoutTxAsHex = nonEmptyStringOf(message.getPayoutTxAsHex());

        state = State.onPayoutTxPublishedMessage;

        Transaction payoutTx = new Transaction(walletFacade.getWallet().getParams(),
                Utils.parseAsHexOrBase58(payoutTxAsHex));
        listener.onPayoutTxPublished(payoutTx);
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
