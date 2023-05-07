package bisq.core.trade.txproof.xmr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class XmrRawTxParserTest {
    private final XmrRawTxParser parser = new XmrRawTxParser();

    @Test
    public void testJsonRoot() {
        // checking what happens when bad input is provided
        assertSame(parser.parse("invalid json data").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse("").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse("[]").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse("{}").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }

    @Test
    public void testJsonTopLevel() {
        // testing the top level fields: data and status
        assertSame(parser.parse("{'data':{'title':''},'status':'fail'}")
                .getDetail(), XmrTxProofRequest.Detail.TX_NOT_FOUND);
        assertSame(parser.parse("{'data':{'title':''},'missingstatus':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse("{'missingdata':{'title':''},'status':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }

    @Test
    public void testJsonTxUnlockTime() {
        String missing_tx_timestamp = "{'data':{'version':'2'}, 'status':'success'}";
        assertSame(parser.parse(missing_tx_timestamp).getDetail(), XmrTxProofRequest.Detail.API_INVALID);

        String invalid_unlock_time = "{'data':{'unlock_time':'1'}, 'status':'success'}";
        assertSame(parser.parse(invalid_unlock_time).getDetail(), XmrTxProofRequest.Detail.INVALID_UNLOCK_TIME);

        String valid_unlock_time = "{'data':{'unlock_time':'0'}, 'status':'success'}";
        assertSame(parser.parse(valid_unlock_time).getDetail(), XmrTxProofRequest.Detail.SUCCESS);
    }

    @Test
    public void testJsonFail() {
        String failedJson = "{\"data\":null,\"message\":\"Cant parse tx hash: a\",\"status\":\"error\"}";
        assertSame(parser.parse(failedJson).getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }
}
