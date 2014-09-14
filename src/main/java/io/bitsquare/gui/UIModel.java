package io.bitsquare.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UIModel {
    private static final Logger log = LoggerFactory.getLogger(UIModel.class);

    public void initialize() {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
    }

    public void activate() {
        log.trace("Lifecycle: activate " + this.getClass().getSimpleName());
    }

    public void deactivate() {
        log.trace("Lifecycle: deactivate " + this.getClass().getSimpleName());
    }

    public void terminate() {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());
    }
}
