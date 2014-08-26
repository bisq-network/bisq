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

package io.bitsquare.trade.protocol.old;

import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO not used but let it for reference until all use cases are impl.
@SuppressWarnings({"WeakerAccess", "EmptyMethod"})
public class PaymentProcess {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcess.class);
    protected String offererDepositPubKey;
    protected String offererPayoutAddress;
    protected String offererChangeAddress;
    protected String offererTotalInputPayment;
    protected String offererOutputPayment;
    protected String takerDepositPubKey;
    protected String takerPayoutAddress;
    protected String takerChangeAddress;
    protected String takerTotalInputPayment;
    protected String takerOutputPayment;
    protected String multiSigAddress;

    public PaymentProcess() {
    }

    @Inject
    public void setMessageService(MessageFacade messageService) {
    }

    @Inject
    public void setWallet(WalletFacade wallet) {
    }

    @Inject
    public void setBtcServices(BlockChainFacade bitcoinServices) {
    }


    public void executeStep0() {
    }

    public void executeStep1() {
    }

    public void executeStep2() {
    }

    public void executeStep3() {
    }


    protected void createDepositTx() {
        //wallet.getInputs(offererTotalInputPayment);
        //bitcoinServices.createTx(taker);
    }

    protected void payPaymentAndCollateral() {
    }

    protected void signDepositTx() {
    }

    protected void sendDataDepositTx() {
    }

    protected void onMessageFiatTxInited() {
    }

    protected void onUserInputFiatReceived() {
    }

    protected void onDataPayoutTx() {
    }

    protected void onMessageDepositTxPublished() {
    }

    protected void signPayoutTx() {
    }

    protected void publishPayoutTx() {
    }

    protected void sendMessagePayoutTxPublished() {
    }

    protected void onBlockChainConfirmation() {
    }

    protected void done() {
    }

    protected void onDataDepositTx() {
    }

    protected void payCollateral() {
    }

    protected void publishDepositTx() {
    }

    protected void sendMessageDepositTxPublished() {
    }

    protected void payFiat() {
    }

    protected void sendMessageFiatTxInited() {
    }

    protected void createPayoutTx() {
    }

    protected void sendDataPayoutTx() {
    }

    protected void onMessagePayoutTxPublished() {
    }

    /*
    case 1:
    BUY offer
    taker:
        1   PAY BTC
            create ms
            create deposit tx
            pay payment+coll
            signContract
            send deposit tx to offerer
        2   WAIT FOR FIAT
            wait for pub tx info msg
            wait for build fiat info msg
            wait for fiat on bank
            wait for payout tx
        3   RELEASE BTC
            signContract payout tx
            pub payout tx
            send info to offerer
            wait for >= 1 confirm
            DONE
    offerer:
        1   WAIT FOR BTC PAYMENT
            wait for deposit tx
            pay coll
            signContract
            pub deposit tx
            send info msg to taker
            wait for >=1 confirm
        2   PAY FIAT
            build fiat
            send info msg to taker
            create payout tx
            signContract payout tx
            send payout tx to taker
        3   WAIT FOR BTC RELEASE
            wait for release info msg
            wait for >= 1 confirm
            DONE

    case 2:
    SELL offer
    taker:
        1   PAY COLL
            create ms
            create deposit tx
            pay coll
            signContract
            send deposit tx to offerer
        2   WAIT FOR BTC PAYMENT
            wait for pub tx info msg
            wait for >=1 confirm
        3   PAY FIAT -> Same
            build fiat
            send info msg to taker
            create payout tx
            signContract payout tx
            send payout tx to offerer
        4   WAIT FOR BTC RELEASE -> Same
            wait for release info msg
            wait for >= 1 confirm
            DONE
    offerer:
        1   WAIT FOR COLL
            wait for deposit tx
        2   PAY BTC
            pay coll+payment
            signContract
            pub deposit tx
            send info msg to taker
        3   WAIT FOR FIAT
            wait for build fiat info msg
            wait for payout tx
            wait for fiat on bank
        4   RELEASE BTC
            signContract payout tx
            pub payout tx
            send info to taker
            wait for >= 1 confirm
            DONE


     */
}
