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

import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.protocol.Protocol;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import java.io.IOException;
import java.io.Serializable;

import java.util.Date;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient protected static final Logger log = LoggerFactory.getLogger(Trade.class);


    public interface ProcessState {
    }

    public interface LifeCycleState {
    }

    transient protected Protocol protocol;
    protected MailboxMessage mailboxMessage;

    protected final Offer offer;
    protected final Date date;

    protected Coin tradeAmount;
    protected Contract contract;
    protected String contractAsJson;
    protected String takerContractSignature;
    protected String offererContractSignature;
    protected Transaction depositTx;
    protected Transaction payoutTx;
    protected Peer tradingPeer;
    protected int depthInBlocks = 0;

    transient protected String errorMessage;
    transient protected Throwable throwable;
    transient protected ObjectProperty<Coin> tradeAmountProperty = new SimpleObjectProperty<>(tradeAmount);
    transient protected ObjectProperty<Fiat> tradeVolumeProperty = new SimpleObjectProperty<>(getTradeVolume());


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade(Offer offer) {
        this.offer = offer;
        date = new Date();
    }

    // Serialized object does not create our transient objects
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        tradeAmountProperty = new SimpleObjectProperty<>(tradeAmount);
        tradeVolumeProperty = new SimpleObjectProperty<>(getTradeVolume());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protocol
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;

        if (mailboxMessage != null)
            protocol.setMailboxMessage(mailboxMessage);
    }

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }


    public void setTradingPeer(Peer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    public void setOffererContractSignature(String offererContractSignature) {
        this.offererContractSignature = offererContractSignature;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public Contract getContract() {
        return contract;
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

    public Fiat getTradeVolume() {
        return offer.getVolumeByAmount(tradeAmount);
    }

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

    public Peer getTradingPeer() {
        return tradingPeer;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    abstract public ReadOnlyObjectProperty<? extends ProcessState> processStateProperty();

    abstract public ReadOnlyObjectProperty<? extends LifeCycleState> lifeCycleStateProperty();


    @Override
    public String toString() {
        return "Trade{" +
                "protocol=" + protocol +
                ", mailboxMessage=" + mailboxMessage +
                ", offer=" + offer +
                ", date=" + date +
                ", tradeAmount=" + tradeAmount +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", takerContractSignature='" + takerContractSignature + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
                ", depositTx=" + depositTx +
                ", payoutTx=" + payoutTx +
                ", tradingPeer=" + tradingPeer +
                ", depthInBlocks=" + depthInBlocks +
                '}';
    }
}
