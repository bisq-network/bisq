package io.bitsquare.msg;

import io.bitsquare.msg.actor.DHTManager;
import io.bitsquare.msg.actor.command.InitializePeer;
import io.bitsquare.util.ActorService;

import com.google.inject.Inject;

import java.util.List;

import net.tomp2p.peers.Number160;

import akka.actor.ActorSystem;

public class DHTSeedService extends ActorService {

    private static final List<SeedNodeAddress.StaticSeedNodeAddresses> staticSedNodeAddresses = SeedNodeAddress
            .StaticSeedNodeAddresses.getAllSeedNodeAddresses();

    @Inject
    public DHTSeedService(ActorSystem system) {
        super(system, "/user/" + DHTManager.SEED_NAME);
    }

    public void initializePeer() {

        // TODO hard coded seed peer config for now, should read from config properties file
        send(new InitializePeer(new Number160(5001), 5001, null));
    }
}
