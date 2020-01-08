package bisq.core.btc.nodes;

import java.net.ServerSocket;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalBitcoinNodeTests {

    private AtomicBoolean called = new AtomicBoolean(false);
    private Runnable callback = () -> called.set(true);
    private int port;
    private LocalBitcoinNode localBitcoinNode;

    @Before
    public void setUp() throws IOException {
        // Find an available port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        localBitcoinNode = new LocalBitcoinNode(port);
    }

    @Test
    public void whenLocalBitcoinNodeIsDetected_thenCallbackGetsRun_andIsDetectedReturnsTrue() {
        // Listen on the port, indicating that the local bitcoin node is running
        new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(port)){
                socket.accept();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).start();

        localBitcoinNode.detectAndRun(callback);
        assertTrue(called.get());
        assertTrue(localBitcoinNode.isDetected());
    }

    @Test
    public void whenLocalBitcoinNodeIsNotDetected_thenCallbackGetsRun_andIsDetectedReturnsFalse() {
        // Leave port unbound, indicating that no local Bitcoin node is running
        localBitcoinNode.detectAndRun(callback);
        assertTrue(called.get());
        assertFalse(localBitcoinNode.isDetected());
    }
}
