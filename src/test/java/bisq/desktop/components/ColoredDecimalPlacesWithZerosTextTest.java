package bisq.desktop.components;

import javafx.scene.control.Label;
import javafx.scene.text.Text;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColoredDecimalPlacesWithZerosTextTest {

    @Ignore
    public void testOnlyZeroDecimals() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.0000", 3);
        Label beforeZeros = (Label) text.getChildren().get(0);
        Label zeroDecimals = (Label) text.getChildren().get(1);
        assertEquals("1.0", beforeZeros.getText());
        assertEquals("000", zeroDecimals.getText());
    }

    @Ignore
    public void testOneZeroDecimal() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.2570", 3);
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        assertEquals("1.257", beforeZeros.getText());
        assertEquals("0", zeroDecimals.getText());
    }

    @Ignore
    public void testMultipleZeroDecimal() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.2000", 3);
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        assertEquals("1.2", beforeZeros.getText());
        assertEquals("000", zeroDecimals.getText());
    }

    @Ignore
    public void testZeroDecimalsWithRange() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("0.1000 - 0.1250", 3);
        assertEquals(5, text.getChildren().size());
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        Text separator = (Text) text.getChildren().get(2);
        Text beforeZeros2 = (Text) text.getChildren().get(3);
        Text zeroDecimals2 = (Text) text.getChildren().get(4);
        assertEquals("0.1", beforeZeros.getText());
        assertEquals("000", zeroDecimals.getText());
        assertEquals(" - ", separator.getText());
        assertEquals("0.125", beforeZeros2.getText());
        assertEquals("0", zeroDecimals2.getText());
    }

    @Test
    public void testNoColorizing() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.2570", 0);
        Text beforeZeros = (Text) text.getChildren().get(0);
        assertEquals("1.2570", beforeZeros.getText());
    }

}
