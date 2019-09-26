package bisq.core.app;

import bisq.common.util.Utilities;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OSXStandbyModeDisabler {
    public void doIt() {
        if (!Utilities.isOSX()) {
            return;
        }
        long pid = ProcessHandle.current().pid();
        try {
            String[] params = {"/usr/bin/caffeinate", "-w", "" + pid};

            // we only start the process. caffeinate blocks until we exit.
            new ProcessBuilder(params).start();
            log.info("disabled power management via " + String.join(" ", params));
        } catch (IOException e) {
            log.error("could not disable standby mode on osx", e);
        }

    }
}
