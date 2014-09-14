package io.bitsquare.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresentationModel<T extends UIModel> {
    private static final Logger log = LoggerFactory.getLogger(PresentationModel.class);

    protected T model;

    public PresentationModel(T model) {
        this.model = model;
    }

    // TODO Still open question if we enforce a model or not? For small UIs it might be too much overhead.
    public PresentationModel() {
    }

    public void initialize() {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
        if (model != null)
            model.initialize();
    }

    public void activate() {
        log.trace("Lifecycle: activate " + this.getClass().getSimpleName());
        if (model != null)
            model.activate();
    }

    public void deactivate() {
        log.trace("Lifecycle: deactivate " + this.getClass().getSimpleName());
        if (model != null)
            model.deactivate();
    }

    public void terminate() {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());
        if (model != null)
            model.terminate();
    }
}
