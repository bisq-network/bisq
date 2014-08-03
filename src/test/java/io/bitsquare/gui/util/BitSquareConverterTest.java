package io.bitsquare.gui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BitSquareConverterTest
{

    @Test
    public void testStringToDouble()
    {

        assertEquals(1, BitSquareConverter.stringToDouble("1"), 0);
        assertEquals(0.1, BitSquareConverter.stringToDouble("0.1"), 0);
        assertEquals(0.1, BitSquareConverter.stringToDouble("0,1"), 0);
        assertEquals(1, BitSquareConverter.stringToDouble("1.0"), 0);
        assertEquals(1, BitSquareConverter.stringToDouble("1,0"), 0);

        assertEquals(0, BitSquareConverter.stringToDouble("1,000.2"), 0);
        assertEquals(0, BitSquareConverter.stringToDouble("1,000.2"), 0);
        assertEquals(0, BitSquareConverter.stringToDouble(null), 0);
        assertEquals(0, BitSquareConverter.stringToDouble(""), 0);
        assertEquals(0, BitSquareConverter.stringToDouble(""), 0);
        assertEquals(0, BitSquareConverter.stringToDouble("."), 0);
        assertEquals(0, BitSquareConverter.stringToDouble(","), 0);
        assertEquals(0, BitSquareConverter.stringToDouble("a"), 0);
    }
}
