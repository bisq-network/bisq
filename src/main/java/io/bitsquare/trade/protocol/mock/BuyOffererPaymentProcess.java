package io.bitsquare.trade.protocol.mock;

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
