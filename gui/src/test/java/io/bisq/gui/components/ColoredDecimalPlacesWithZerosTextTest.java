package io.bisq.gui.components;

import javafx.scene.text.Text;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColoredDecimalPlacesWithZerosTextTest {

    @Test
    public void testOnlyZeroDecimals() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.0000");
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        assertEquals("1.0", beforeZeros.getText());
        assertEquals("000", zeroDecimals.getText());
    }

    @Test
    public void testOneZeroDecimal() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.2570");
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        assertEquals("1.257", beforeZeros.getText());
        assertEquals("0", zeroDecimals.getText());
    }

    @Test
    public void testMultipleZeroDecimal() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("1.2000");
        Text beforeZeros = (Text) text.getChildren().get(0);
        Text zeroDecimals = (Text) text.getChildren().get(1);
        assertEquals("1.2", beforeZeros.getText());
        assertEquals("000", zeroDecimals.getText());
    }

    @Test
    public void testZeroDecimalsWithRange() {
        ColoredDecimalPlacesWithZerosText text = new ColoredDecimalPlacesWithZerosText("0.1000 - 0.1250");
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

}
