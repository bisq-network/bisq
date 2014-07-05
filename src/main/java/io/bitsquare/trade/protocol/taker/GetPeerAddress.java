package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.GetPeerAddressListener;
import io.bitsquare.trade.protocol.FaultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPeerAddress
{
    private static final Logger log = LoggerFactory.getLogger(GetPeerAddress.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, MessageFacade messageFacade, String messagePubKeyAsHex)
    {
        messageFacade.getPeerAddress(messagePubKeyAsHex, new GetPeerAddressListener()
        {
            @Override
            public void onResult(PeerAddress peerAddress)
            {
                log.trace("Received address = " + peerAddress.toString());
                resultHandler.onResult(peerAddress);
            }

            @Override
            public void onFailed()
            {
                log.error("Lookup for peer address faultHandler.onFault.");
                faultHandler.onFault(new Exception("Lookup for peer address faultHandler.onFault."));
            }
        });
    }

    public interface ResultHandler
    {
        void onResult(PeerAddress peerAddress);
    }
}

