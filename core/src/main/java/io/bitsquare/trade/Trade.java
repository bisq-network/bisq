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

import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.Protocol;
import io.bitsquare.trade.protocol.trade.taker.models.TakerTradeProcessModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import java.io.Serializable;

import java.util.Date;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

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


    public interface ProcessState {
    }

    public interface LifeCycleState {
    }

    protected MailboxMessage mailboxMessage;

    protected final Offer offer;
    protected final Date date;

    protected Protocol protocol;
    protected Contract contract;
    protected String contractAsJson;
    protected String takerContractSignature;
    protected String offererContractSignature;
    protected Transaction depositTx;
    protected Transaction payoutTx;
    protected int depthInBlocks = 0;

    transient protected String errorMessage;
    transient protected Throwable throwable;
    transient protected ObjectProperty<Coin> tradeAmountProperty = new SimpleObjectProperty<>();
    transient protected ObjectProperty<Fiat> tradeVolumeProperty = new SimpleObjectProperty<>();

    transient private Storage<TakerTradeProcessModel> storage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade(Offer offer, Storage storage) {
        this.offer = offer;
        this.storage = storage;

        date = new Date();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The deserialized tx has not actual confidence data, so we need to get the fresh one from the wallet.
    public void updateTxFromWallet(TradeWalletService tradeWalletService) {
        if (depositTx != null)
            setDepositTx(tradeWalletService.commitsDepositTx(depositTx));
    }

    public void setDepositTx(Transaction tx) {
        this.depositTx = tx;
        setConfidenceListener();
    }

    public void reActivate() {
        if (mailboxMessage != null)
            protocol.setMailboxMessage(mailboxMessage);
    }

    // Get called from taskRunner after each completed task
    @Override
    public void persist() {
        storage.save();
    }

    @Override
    public void onComplete() {
        storage.save();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protocol
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void disposeProtocol() {
        if (protocol != null) {
            protocol.cleanup();
            protocol = null;
        }
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
        if (protocol != null)
            protocol.setMailboxMessage(mailboxMessage);
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    public void setOffererContractSignature(String offererContractSignature) {
        this.offererContractSignature = offererContractSignature;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public void setPayoutTx(Transaction tx) {
        this.payoutTx = tx;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public String getOffererContractSignature() {
        return offererContractSignature;
    }

    public Transaction getDepositTx() {
        return depositTx;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

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

    public String getContractAsJson() {
        return contractAsJson;
    }

    public Date getDate() {
        return date;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    public abstract ReadOnlyObjectProperty<? extends ProcessState> processStateProperty();

    public abstract ReadOnlyObjectProperty<? extends LifeCycleState> lifeCycleStateProperty();

    public abstract Coin getTradeAmount();

    public abstract Fiat getTradeVolume();

    public abstract Peer getTradingPeer();


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
                ", depthInBlocks=" + depthInBlocks +
                '}';
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void setConfidenceListener();


}
