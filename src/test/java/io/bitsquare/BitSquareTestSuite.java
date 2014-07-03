package io.bitsquare;

import io.bitsquare.btc.BtcValidatorTest;
import io.bitsquare.gui.util.BitSquareConverterTest;
import io.bitsquare.gui.util.BitSquareValidatorTest;
import io.nucleo.scheduler.SequenceSchedulerTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        BtcValidatorTest.class,
        BitSquareConverterTest.class,
        BitSquareValidatorTest.class,
        SequenceSchedulerTest.class
})

public class BitSquareTestSuite
{
}
