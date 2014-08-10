package io.bitsquare;

import io.bitsquare.btc.BtcValidatorTest;
import io.bitsquare.gui.util.BitSquareConverterTest;
import io.bitsquare.gui.util.BitSquareNumberValidatorTest;
import io.bitsquare.gui.util.FiatValidatorTest;
import io.bitsquare.msg.P2PNodeTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
                            BtcValidatorTest.class,
                            BitSquareConverterTest.class,
                            BitSquareNumberValidatorTest.class,
                            P2PNodeTest.class,
                            FiatValidatorTest.class,
                            BtcValidatorTest.class
                    })

public class BitSquareTestSuite
{
}
