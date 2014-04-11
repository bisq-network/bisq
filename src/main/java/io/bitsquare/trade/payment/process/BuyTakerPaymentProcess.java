package io.bitsquare.trade.payment.process;

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
