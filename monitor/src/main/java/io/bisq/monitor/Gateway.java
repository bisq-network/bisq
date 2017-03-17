package io.bisq.monitor;

import io.bisq.offer.Offer;
import io.bisq.offer.OfferBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.util.List;

public class Gateway {
    private static final Logger log = LoggerFactory.getLogger(Gateway.class);
    private OfferBookService offerBookService;

    public Gateway(OfferBookService offerBookService) {
        this.offerBookService = offerBookService;

        GatewayServer gatewayServer = new GatewayServer(this);
        gatewayServer.start();
        log.info("Gateway Server Started");
    }

    public List<Offer> getOffers() {
        return offerBookService.getOffers();
    }
}
