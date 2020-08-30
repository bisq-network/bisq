package bisq.core.trade.asset.xmr;

import bisq.core.trade.AutoConfirmResult;

import java.time.Instant;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XmrProofInfoTest {
    private XmrProofInfo xmrProofInfo;
    private String recipientAddressHex = "e957dac72bcec80d59b2fecacfa7522223b6a5df895b7e388e60297e85f3f867b42f43e8d9f086a99a997704ceb92bd9cd99d33952de90c9f5f93c82c62360ae";
    private String txHash = "488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d";
    private String txKey = "6c336e52ed537676968ee319af6983c80b869ca6a732b5962c02748b486f8f0f";
    private String recipientAddress = "4ATyxmFGU7h3EWu5kYR6gy6iCNFCftbsjATfbuBBjsRHJM4KTwEyeiyVNNUmsfpK1kdRxs8QoPLsZanGqe1Mby43LeyWNMF";

    @Before
    public void prepareMocksAndObjects() {

        long amount = 100000000000L;
        Date tradeDate = Date.from(Instant.now());
        int confirmsRequired = 10;
        String serviceAddress = "127.0.0.1:8081";

        xmrProofInfo = new XmrProofInfo(
                txHash,
                txKey,
                recipientAddress,
                amount,
                tradeDate,
                confirmsRequired,
                serviceAddress);
    }

    @Test
    public void testKey() {
        assertTrue(xmrProofInfo.getKey().contains(xmrProofInfo.getTxHash()));
        assertTrue(xmrProofInfo.getKey().contains(xmrProofInfo.getServiceAddress()));
        assertFalse(xmrProofInfo.getKey().contains(xmrProofInfo.getRecipientAddress()));
    }

    @Test
    public void testJsonRoot() {
        // checking what happens when bad input is provided
        assertTrue(xmrProofInfo.checkApiResponse(
                "invalid json data").getState() == AutoConfirmResult.State.API_INVALID);
        assertTrue(xmrProofInfo.checkApiResponse(
                "").getState() == AutoConfirmResult.State.API_INVALID);
        assertTrue(xmrProofInfo.checkApiResponse(
                "[]").getState() == AutoConfirmResult.State.API_INVALID);
        assertTrue(xmrProofInfo.checkApiResponse(
                "{}").getState() == AutoConfirmResult.State.API_INVALID);
    }

    @Test
    public void testJsonTopLevel() {
        // testing the top level fields: data and status
        assertTrue(xmrProofInfo.checkApiResponse(
                "{'data':{'title':''},'status':'fail'}" )
                .getState() == AutoConfirmResult.State.TX_NOT_FOUND);
        assertTrue(xmrProofInfo.checkApiResponse(
                "{'data':{'title':''},'missingstatus':'success'}" )
                .getState() == AutoConfirmResult.State.API_INVALID);
        assertTrue(xmrProofInfo.checkApiResponse(
                "{'missingdata':{'title':''},'status':'success'}" )
                .getState() == AutoConfirmResult.State.API_INVALID);
    }

    @Test
    public void testJsonAddress() {
        assertTrue(xmrProofInfo.checkApiResponse(
                "{'data':{'missingaddress':'irrelevant'},'status':'success'}" )
                .getState() == AutoConfirmResult.State.API_INVALID);
        assertTrue(xmrProofInfo.checkApiResponse(
                "{'data':{'address':'e957dac7'},'status':'success'}" )
                .getState() == AutoConfirmResult.State.ADDRESS_INVALID);
    }

    @Test
    public void testJsonTxHash() {
        String missing_tx_hash = "{'data':{'address':'" + recipientAddressHex + "'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(missing_tx_hash).getState()
                == AutoConfirmResult.State.API_INVALID);

        String invalid_tx_hash = "{'data':{'address':'" + recipientAddressHex + "', 'tx_hash':'488e48'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(invalid_tx_hash).getState()
                == AutoConfirmResult.State.TX_HASH_INVALID);
    }

    @Test
    public void testJsonTxKey() {
        String missing_tx_key = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(missing_tx_key).getState()
                == AutoConfirmResult.State.API_INVALID);

        String invalid_tx_key = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'cdce04'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(invalid_tx_key).getState()
                == AutoConfirmResult.State.TX_KEY_INVALID);
    }

    @Test
    public void testJsonTxTimestamp() {
        String missing_tx_timestamp = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "'," +
                "'viewkey':'" + txKey + "'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(missing_tx_timestamp).getState()
                == AutoConfirmResult.State.API_INVALID);

        String invalid_tx_timestamp = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "'," +
                "'tx_timestamp':'12345'}, 'status':'success'}";
        assertTrue(xmrProofInfo.checkApiResponse(invalid_tx_timestamp).getState()
                == AutoConfirmResult.State.TRADE_DATE_NOT_MATCHING);
    }

    @Test
    public void testJsonTxConfirmation() {
        long epochDate = Instant.now().toEpochMilli() / 1000;
        String outputs = "'outputs':[" +
                "{'amount':100000000000,'match':true,'output_idx':0,'output_pubkey':'972a2c9178876f1fae4ecd22f9d7c132a12706db8ffb5d1f223f9aa8ced75b61'}," +
                "{'amount':0,'match':false,'output_idx':1,'output_pubkey':'658330d2d56c74aca3b40900c56cd0f0111e2876be677ade493d06d539a1bab0'}],";
        String json = "{'status':'success', 'data':{" +
                "'address':'" + recipientAddressHex + "', " +
                outputs +
                "'tx_confirmations':777, " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "', " +
                "'tx_timestamp':'" + Long.toString(epochDate) + "'}" +
                "}";
        assertTrue(xmrProofInfo.checkApiResponse(json).getState()
                == AutoConfirmResult.State.PROOF_OK);
        json = json.replaceFirst("777", "0");
        assertTrue(xmrProofInfo.checkApiResponse(json).getState()
                == AutoConfirmResult.State.TX_NOT_CONFIRMED);
        json = json.replaceFirst("100000000000", "100000000001");
        assertTrue(xmrProofInfo.checkApiResponse(json).getState()
                == AutoConfirmResult.State.AMOUNT_NOT_MATCHING);

        // Revert change of amount
        json = json.replaceFirst("100000000001", "100000000000");
        json = json.replaceFirst("'match':true", "'match':false");
        assertTrue(xmrProofInfo.checkApiResponse(json).getState()
                == AutoConfirmResult.State.NO_MATCH_FOUND);
    }

    @Test
    public void testJsonFail() {
        String failedJson = "{\"data\":null,\"message\":\"Cant parse tx hash: a\",\"status\":\"error\"}";
        assertTrue(xmrProofInfo.checkApiResponse(failedJson).getState()
                == AutoConfirmResult.State.API_INVALID);
    }
}
