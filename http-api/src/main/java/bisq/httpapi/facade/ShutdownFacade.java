package bisq.httpapi.facade;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShutdownFacade {

    @Setter
    public Runnable shutdownHandler;

    public boolean isShutdownSupported() {
        return null != shutdownHandler;
    }

    public void shutDown() {
        if (!isShutdownSupported()) {
            log.warn("Shutdown is not supported");
        } else {
            shutdownHandler.run();
        }
    }
}
