package bisq.common.config;

import joptsimple.HelpFormatter;
import joptsimple.OptionParser;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class HelpRequested extends RuntimeException {

    private final OptionParser parser;

    public HelpRequested(OptionParser parser) {
        this.parser = parser;
    }

    public void printHelp(OutputStream sink, HelpFormatter formatter) {
        try {
            parser.formatHelpWith(formatter);
            parser.printHelpOn(sink);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
