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

package io.bitsquare.payment.unused;

import io.bitsquare.app.Version;
import io.bitsquare.payment.PaymentAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class USPostalMoneyOrderAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(USPostalMoneyOrderAccount.class);

    private String holderName;
    private String iban;
    private String bic;

    /*
 - Message (interface):
     - GetDataRequest (Interface)
         - PreliminaryGetDataRequest (final class) impl. AnonymousMessage
         - GetUpdatedDataRequest (final class) impl. SendersNodeAddressMessage
     - SendersNodeAddressMessage (Interface)
         - PrefixedSealedAndSignedMessage (final class) impl. MailboxMessage
         - GetUpdatedDataRequest (final class) impl. GetDataRequest
         - GetPeersRequest (final class) extends PeerExchangeMessage	
     - AnonymousMessage (Interface)
         - PreliminaryGetDataRequest (final class) impl. GetDataRequest
     - DirectMessage (Interface)
         - OfferMessage (abstract class)
             - OfferAvailabilityRequest (final class) 
             - OfferAvailabilityResponse (final class) 
         - TradeMessage (abstract class)
             - DepositTxPublishedMessage (final class) implements MailboxMessage
             - FiatTransferStartedMessage (final class) implements MailboxMessage
             - FinalizePayoutTxRequest (final class) implements MailboxMessage
             - PayDepositRequest (final class) implements MailboxMessage
             - PayoutTxFinalizedMessage (final class) implements MailboxMessage
             - PublishDepositTxRequest (final class) 
         - DecryptedMsgWithPubKey (final class)
         - MailboxMessage (Interface)
             - PrefixedSealedAndSignedMessage (final class) implements SendersNodeAddressMessage
             - DisputeMessage (abstract class)
                 - DisputeCommunicationMessage (final class) 
                 - DisputeResultMessage (final class) 
                 - OpenNewDisputeMessage (final class) 
                 - PeerOpenedDisputeMessage (final class) 
                 - PeerPublishedPayoutTxMessage (final class) 
             - DepositTxPublishedMessage (final class) extends TradeMessage
             - FiatTransferStartedMessage (final class) extends TradeMessage
             - FinalizePayoutTxRequest (final class) extends TradeMessage
             - PayDepositRequest (final class) extends TradeMessage
             - PayoutTxFinalizedMessage (final class) extends TradeMessage
     - DataBroadcastMessage (abstract class)
         - AddDataMessage (final class)
         - RefreshTTLMessage (final class)
         - RemoveDataMessage (final class)
         - RemoveMailboxDataMessage (final class)
     - PeerExchangeMessage (abstract class)
         - GetPeersRequest (final class) implements SendersNodeAddressMessage 
         - GetPeersResponse (final class) 
     - KeepAliveMessage (abstract class)
         - Ping (final class)   
         - Pong (final class) 
     - GetDataResponse (final class)
     - CloseConnectionMessage (final class)
     */
    private USPostalMoneyOrderAccount() {
        super(null /*PaymentMethod.US_POSTAL_MONEY_ORDER*/);
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    @Override
    public String getPaymentDetails() {
        return "{accountName='" + accountName + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "USPostalMoneyOrderAccount{" +
                "accountName='" + accountName + '\'' +
                ", id='" + id + '\'' +
                ", paymentMethod=" + paymentMethod +
                ", holderName='" + holderName + '\'' +
                ", iban='" + iban + '\'' +
                ", bic='" + bic + '\'' +
                ", country=" + country +
                ", tradeCurrencies='" + getTradeCurrencies() + '\'' +
                ", selectedTradeCurrency=" + selectedTradeCurrency +
                '}';
    }
}
