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

//TODO not used but let it for reference until all use cases are impl.
class BuyOffererPaymentProcess extends PaymentProcess
{
    public BuyOffererPaymentProcess()
    {
        super();
    }


    // case 1 offerer step 1
    private void buyOfferOfferer_payToDeposit()
    {
        onDataDepositTx();
        payCollateral();
        signDepositTx();
        publishDepositTx();
        sendMessageDepositTxPublished();
        onBlockChainConfirmation();
    }

    // case 1 offerer step 2
    private void buyOfferOfferer_payToDeposist()
    {
        payFiat();
        sendMessageFiatTxInited();
        createPayoutTx();
        signPayoutTx();
        sendDataPayoutTx();
        onBlockChainConfirmation();
    }

    // case 1 offerer step 3
    private void buyOfferOfferer_waitForRelease()
    {
        onMessagePayoutTxPublished();
        onBlockChainConfirmation();
        done();
    }
}
