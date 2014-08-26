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
class SellOffererPaymentProcess extends PaymentProcess
{
    public SellOffererPaymentProcess()
    {
        super();
    }


    // case 2 offerer step 1
    private void sellOfferOfferer_waitForCollateralPayedByPeer()
    {
        onDataDepositTx();
    }

    // case 2 offerer step 2
    private void sellOfferOfferer_payToDeposit()
    {
        payPaymentAndCollateral();
        signDepositTx();
        publishDepositTx();
        sendMessageDepositTxPublished();
    }

    // case 2 offerer step 3
    private void sellOfferOfferer_waitForFiat()
    {
        onMessageFiatTxInited();
        onDataPayoutTx();
        onUserInputFiatReceived();
    }

    // case 2 offerer step 4
    private void sellOfferOfferer_releasePayment()
    {
        signPayoutTx();
        publishPayoutTx();
        sendMessagePayoutTxPublished();
        onBlockChainConfirmation();
        done();
    }

}
