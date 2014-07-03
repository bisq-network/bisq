package io.bitsquare.trade.payment.taker.tasks;

import com.google.bitcoin.core.Utils;
import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.taker.messages.RequestOffererPublishDepositTxMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedTakerDepositTxAsHex extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(SendSignedTakerDepositTxAsHex.class);

    public SendSignedTakerDepositTxAsHex(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");

        RequestOffererPublishDepositTxMessage tradeMessage = new RequestOffererPublishDepositTxMessage(sharedModel.getTrade().getId(),
                sharedModel.getUser().getCurrentBankAccount(),
                sharedModel.getUser().getAccountID(),
                sharedModel.getUser().getMessagePubKeyAsHex(),
                Utils.bytesToHexString(sharedModel.getSignedTakerDepositTx().bitcoinSerialize()),
                Utils.bytesToHexString(sharedModel.getSignedTakerDepositTx().getInput(1).getScriptBytes()),
                Utils.bytesToHexString(sharedModel.getSignedTakerDepositTx().getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize()),
                sharedModel.getTrade().getContractAsJson(),
                sharedModel.getTrade().getTakerSignature(),
                sharedModel.getWalletFacade().getAddressInfoByTradeID(sharedModel.getTrade().getId()).getAddressString(),
                sharedModel.getTakerTxOutIndex(),
                sharedModel.getOffererTxOutIndex()
        );
        sharedModel.getMessageFacade().sendTradeMessage(sharedModel.getPeerAddress(), tradeMessage, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestOffererDepositPublicationMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestOffererDepositPublicationMessage failed to arrive at peer");
                failed(new Exception("RequestOffererDepositPublicationMessage failed to arrive at peer"));
            }
        });
    }
}
