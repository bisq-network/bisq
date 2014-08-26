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
public class BuyTakerPaymentProcess extends PaymentProcess
{

    public BuyTakerPaymentProcess()
    {
        super();
    }

    @Override
    public void executeStep0()
    {
        // bitcoinServices.createMultiSig();
        createDepositTx();
        payPaymentAndCollateral();
        signDepositTx();
        sendDataDepositTx();
    }

    @Override
    public void executeStep1()
    {
        onMessageDepositTxPublished();
        onMessageFiatTxInited();
        onUserInputFiatReceived();
        onDataPayoutTx();
    }

    @Override
    public void executeStep2()
    {
        signPayoutTx();
        publishPayoutTx();
        sendMessagePayoutTxPublished();
        onBlockChainConfirmation();
        done();
    }
}
