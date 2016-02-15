package io.bitsquare.gui.main.intructions;

import com.google.inject.Inject;
import io.bitsquare.trade.TradeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstructionCenter {
    private final Logger log = LoggerFactory.getLogger(InstructionCenter.class);

    private final TradeManager tradeManager;

    @Inject
    public InstructionCenter(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }
}
