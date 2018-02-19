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

}
