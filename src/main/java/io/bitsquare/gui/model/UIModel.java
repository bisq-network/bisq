package io.bitsquare.gui.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UIModel<T> {
    private static final Logger log = LoggerFactory.getLogger(UIModel.class);

    public void initialized() {
        activate();
    }

    public void activate() {
    }

    public void deactivate() {
    }

    public void terminate() {
        deactivate();
    }
}
