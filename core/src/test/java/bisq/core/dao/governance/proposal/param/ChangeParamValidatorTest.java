package bisq.core.dao.governance.proposal.param;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.common.proto.persistable.PersistenceProtoResolver;

import java.io.File;

import mockit.Injectable;
import mockit.Tested;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ChangeParamValidatorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    // @Tested classes are instantiated automatically when needed in a test case,
    // using injection where possible, see http://jmockit.github.io/tutorial/Mocking.html#tested
    // To force instantiate earlier, use availableDuringSetup
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    DaoStateService daoStateService;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    PeriodService periodService;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    BsqFormatter bsqFormatter;
    // @Injectable are mocked resources used to for injecting into @Tested classes
    // The naming of these resources doesn't matter, any resource that fits will be used for injection

    // Used by bsqStateService
    @Injectable
    PersistenceProtoResolver persistenceProtoResolver;
    @Injectable
    File storageDir;
    @Injectable
    String genesisTxId = "genesisTxId";
    @Injectable
    Integer genesisBlockHeight = 200;

    // Used by periodService
    @Injectable
    int chainHeight = 400;

    @Before
    public void setUp() {
        Res.setup();
    }
//-1
    @Test
    public void testCheckMinMaxForProposedValueZero() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValue = 0;
        long maxFactorChange = 2;
        long minFactorChange = 2;

        thrown.expect(ChangeParamValidationException.class);
        thrown.expectMessage("Input cannot be zero.");
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.checkMinMaxForProposedValue(param,proposedNewValue,maxFactorChange,minFactorChange);
    }

    @Test
    public void testCheckMinMaxForProposedValueMin() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValue = 50;
        long maxFactorChange = 2;
        long minFactorChange = 2;

        thrown.expect(ChangeParamValidationException.class);
        thrown.expectMessage("Input has to be larger than 50.00%");
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.checkMinMaxForProposedValue(param,proposedNewValue,maxFactorChange,minFactorChange);
    }

    @Test
    public void testCheckMinMaxForProposedValueMax() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValue = 450;
        long maxFactorChange = 2;
        long minFactorChange = 2;

        thrown.expect(ChangeParamValidationException.class);
        thrown.expectMessage("Input must not be larger than 200.00%");
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.checkMinMaxForProposedValue(param,proposedNewValue,maxFactorChange,minFactorChange);
    }


    @Test
    public void testValidateMinValuePos() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValueMinPos = 100;
        long minFactorChange = 2;
        long currentValue = 200;

        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.validateMinValue(param,currentValue,proposedNewValueMinPos,minFactorChange);
    }

    @Test
    public void testValidateMinValueNeg() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValueMinNeg = 99;
        long minFactorChange = 2;
        long currentValue = 200;

        thrown.expect(ChangeParamValidationException.class);
        thrown.expectMessage("Input has to be larger than 50.00%");
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.validateMinValue(param,currentValue,proposedNewValueMinNeg,minFactorChange);
    }

    @Test
    public void testValidateMaxValuePos() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValueMaxPos = 400;
        long maxFactorChange = 2;
        long currentValue = 200;

        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.validateMaxValue(param,currentValue,proposedNewValueMaxPos,maxFactorChange);
    }

    @Test
    public void testValidateMaxValueNeg() throws ChangeParamValidationException {

        Param param = Param.DEFAULT_MAKER_FEE_BSQ;
        long proposedNewValueMaxNeg = 401;
        long maxFactorChange = 2;
        long currentValue = 200;

        thrown.expect(ChangeParamValidationException.class);
        thrown.expectMessage("Input must not be larger than 200.00%");
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(daoStateService,periodService,bsqFormatter);
        changeParamValidator.validateMaxValue(param,currentValue,proposedNewValueMaxNeg,maxFactorChange);

    }
}

