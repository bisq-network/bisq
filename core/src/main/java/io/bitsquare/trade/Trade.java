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

package io.bitsquare.trade;

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.Protocol;
import io.bitsquare.trade.protocol.trade.TradeProcessModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import java.io.Serializable;

import java.util.Date;

import javax.annotation.Nullable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all data which are relevant to the trade, but not those which are only needed in the trade process as shared data between tasks. Those data are
 * stored in the task model.
 */
abstract public class Trade extends Model implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient protected static final Logger log = LoggerFactory.getLogger(Trade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interfaces
    ///////////////////////////////////////////////////////////////////////////////////////////

    interface ProcessState {
    }

    public interface LifeCycleState {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final Offer offer;
    @NotNull private final Date date;
    @NotNull protected TradeProcessModel processModel = createProcessModel();

    protected abstract TradeProcessModel createProcessModel();

    @NotNull transient protected Protocol protocol;

    @Nullable MailboxMessage mailboxMessage;
    @Nullable Transaction depositTx;
    @Nullable private Transaction payoutTx;
    @Nullable private Contract contract;
    @Nullable private String contractAsJson;
    @Nullable private String takerContractSignature;
    @Nullable private String offererContractSignature;

    @NotNull transient private Storage<? extends TradeProcessModel> storage;

    @Nullable private transient String errorMessage;
    @Nullable private transient Throwable throwable;
    @NotNull transient ObjectProperty<Coin> tradeAmountProperty = new SimpleObjectProperty<>();
    @NotNull transient ObjectProperty<Fiat> tradeVolumeProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    Trade(Offer offer, @NotNull Storage<? extends TradeProcessModel> storage) {
        this.offer = offer;
        this.storage = storage;

        date = new Date();
    }

    public void initProcessModel(@NotNull MessageService messageService,
                                 @NotNull MailboxService mailboxService,
                                 @NotNull WalletService walletService,
                                 @NotNull TradeWalletService tradeWalletService,
                                 @NotNull BlockChainService blockChainService,
                                 @NotNull SignatureService signatureService,
                                 @NotNull ArbitrationRepository arbitrationRepository,
                                 @NotNull User user) {

        processModel.init(offer,
                messageService,
                mailboxService,
                walletService,
                tradeWalletService,
                blockChainService,
                signatureService,
                arbitrationRepository,
                user);

        createProtocol();
        
        if (mailboxMessage != null)
            protocol.setMailboxMessage(mailboxMessage);
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The deserialized tx has not actual confidence data, so we need to get the fresh one from the wallet.
    public void syncDepositTxWithWallet(@NotNull TradeWalletService tradeWalletService) {
        if (depositTx != null)
            setDepositTx(tradeWalletService.commitsDepositTx(depositTx));
    }

    public void setDepositTx(@NotNull Transaction tx) {
        this.depositTx = tx;
        setConfidenceListener();
    }

    public void disposeProtocol() {
        if (protocol != null) {
            protocol.cleanup();
            protocol = null;
        }
    }

    public void setMailboxMessage(@NotNull MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
        assert protocol != null;
        protocol.setMailboxMessage(mailboxMessage);
    }

    public void setStorage(@NotNull Storage<? extends TradeProcessModel> storage) {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Get called from taskRunner after each completed task
    @Override
    public void persist() {
        storage.queueUpForSave();
    }

    @Override
    public void onComplete() {
        storage.queueUpForSave();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void setConfidenceListener();

    public void setTakerContractSignature(@NotNull String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    public void setOffererContractSignature(@NotNull String offererContractSignature) {
        this.offererContractSignature = offererContractSignature;
    }

    public void setContractAsJson(@NotNull String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    public void setContract(@NotNull Contract contract) {
        this.contract = contract;
    }

    public void setPayoutTx(@NotNull Transaction tx) {
        this.payoutTx = tx;
    }

    public void setErrorMessage(@NotNull String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setThrowable(@NotNull Throwable throwable) {
        this.throwable = throwable;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    @Nullable
    public String getOffererContractSignature() {
        return offererContractSignature;
    }

    @Nullable
    public Transaction getDepositTx() {
        return depositTx;
    }

    @Nullable
    public Transaction getPayoutTx() {
        return payoutTx;
    }

    @Nullable
    public Contract getContract() {
        return contract;
    }

    public Coin getSecurityDeposit() {
        return offer.getSecurityDeposit();
    }

    public String getId() {
        return offer.getId();
    }

    public Offer getOffer() {
        return offer;
    }

    @Nullable
    public String getContractAsJson() {
        return contractAsJson;
    }

    @NotNull
    public Date getDate() {
        return date;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }

    @NotNull
    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    @NotNull
    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    abstract void createProtocol();

    @NotNull
    public abstract ReadOnlyObjectProperty<? extends ProcessState> processStateProperty();

    @NotNull
    public abstract ReadOnlyObjectProperty<? extends LifeCycleState> lifeCycleStateProperty();

    @org.jetbrains.annotations.Nullable
    public abstract Coin getTradeAmount();

    @org.jetbrains.annotations.Nullable
    public abstract Fiat getTradeVolume();

    @org.jetbrains.annotations.Nullable
    public abstract Peer getTradingPeer();

    @NotNull
    @Override
    public String toString() {
        return "Trade{" +
                "protocol=" + protocol +
                ", mailboxMessage=" + mailboxMessage +
                ", offer=" + offer +
                ", date=" + date +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", takerContractSignature='" + takerContractSignature + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
                ", depositTx=" + depositTx +
                ", payoutTx=" + payoutTx +
                '}';
    }

}
