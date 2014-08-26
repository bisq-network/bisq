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
class SellTakerPaymentProcess extends PaymentProcess
{
    public SellTakerPaymentProcess()
    {
        super();
    }


    // case 2 taker step 1
    private void sellOfferTaker_payToDeposit()
    {
        //createMultiSig();
        createDepositTx();
        payCollateral();
        signDepositTx();
        sendDataDepositTx();
    }

    // case 2 taker step 2
    private void sellOfferTaker_waitForDepositPublished()
    {
        onMessageDepositTxPublished();
        onBlockChainConfirmation();
    }

    // case 2 taker step 3
    private void sellOfferTaker_payFiat()
    {
        payFiat();
        sendMessageFiatTxInited();
        createPayoutTx();
        signPayoutTx();
        sendDataPayoutTx();
    }

    // case 2 taker step 4
    private void sellOfferTaker_waitForRelease()
    {
        onMessagePayoutTxPublished();
        onBlockChainConfirmation();
        done();
    }
}
