package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.trade.payment.taker.listeners.GetPeerAddressListener;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPeerAddress extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(GetPeerAddress.class);

    public GetPeerAddress(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        sharedModel.getMessageFacade().getPeerAddress(sharedModel.getTrade().getOffer().getMessagePubKeyAsHex(), new GetPeerAddressListener()
        {
            @Override
            public void onResult(PeerAddress address)
            {
                log.trace("Received address = " + address.toString());
                sharedModel.setPeerAddress(address);
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("Lookup for peer address failed.");
                failed(new Exception("Lookup for peer address failed."));
            }
        });

    }

}
