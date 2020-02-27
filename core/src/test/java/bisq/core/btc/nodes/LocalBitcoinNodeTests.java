package bisq.core.btc.nodes;

import java.net.ServerSocket;

import java.io.IOException;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalBitcoinNodeTests {

    private AtomicBoolean called = new AtomicBoolean(false);
    private Runnable callback = () -> called.set(true);
    private ServerSocket socket;
    private LocalBitcoinNode localBitcoinNode;

    @Before
    public void setUp() throws IOException {
        // Bind to and listen on an available port
        socket = new ServerSocket(0);
        localBitcoinNode = new LocalBitcoinNode(socket.getLocalPort());
    }

    @Test
    public void whenLocalBitcoinNodeIsDetected_thenCallbackGetsRun_andIsDetectedReturnsTrue() {
        // Continue listening on the port, indicating the local Bitcoin node is running
        localBitcoinNode.detectAndRun(callback);
        assertTrue(called.get());
        assertTrue(localBitcoinNode.isDetected());
    }

    @Test
    public void whenLocalBitcoinNodeIsNotDetected_thenCallbackGetsRun_andIsDetectedReturnsFalse() throws IOException {
        // Stop listening on the port, indicating the local Bitcoin node is not running
        socket.close();
        localBitcoinNode.detectAndRun(callback);
        assertTrue(called.get());
        assertFalse(localBitcoinNode.isDetected());
    }
}
