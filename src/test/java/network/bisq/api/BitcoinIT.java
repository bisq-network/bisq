package network.bisq.api;

import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.arquillian.cube.spi.CubeOutput;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class BitcoinIT {

    @DockerContainer
    Container bitcoin = ContainerFactory.createBitcoinContainer();

    @Test
    public void generateBlocks() throws InterruptedException
    {
        final CubeOutput cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "generate", "101");
        assertEquals("Command 'generate 101' should succeed", "", cubeOutput.getError());
        final CubeOutput getbalanceOutput = bitcoin.exec("bitcoin-cli", "-regtest", "getbalance");
        assertEquals("Balance should be 50 BTC", "50.00000000", getbalanceOutput.getStandard().trim());
    }
}
