package io.bitsquare.trade.protocol.mock;

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
