package bisq.desktop.util.normalization;

import javafx.util.StringConverter;

public class IBANNormalizer extends StringConverter<String> {
    @Override
    public String toString(String s) {
        return s;
    }

    @Override
    public String fromString(String s) {
        return s.replaceAll("\\s+", "");
    }
}
