package io.bitsquare.gui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BitSquareConverterTest
{

    @Test
    public void testStringToDouble()
    {

        assertEquals(1, BitSquareFormatter.parseToDouble("1"), 0);
        assertEquals(0.1, BitSquareFormatter.parseToDouble("0.1"), 0);
        assertEquals(0.1, BitSquareFormatter.parseToDouble("0,1"), 0);
        assertEquals(1, BitSquareFormatter.parseToDouble("1.0"), 0);
        assertEquals(1, BitSquareFormatter.parseToDouble("1,0"), 0);

        assertEquals(0, BitSquareFormatter.parseToDouble("1,000.2"), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble("1,000.2"), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble(null), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble(""), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble(""), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble("."), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble(","), 0);
        assertEquals(0, BitSquareFormatter.parseToDouble("a"), 0);
    }
}
