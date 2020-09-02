package bisq.core.trade.txproof.xmr;

import bisq.core.user.AutoConfirmSettings;

import java.time.Instant;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import static bisq.core.trade.txproof.xmr.XmrTxProofParser.MAX_DATE_TOLERANCE;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class XmrTxProofParserTest {
    private XmrTxProofModel xmrTxProofModel;
    private String recipientAddressHex = "e957dac72bcec80d59b2fecacfa7522223b6a5df895b7e388e60297e85f3f867b42f43e8d9f086a99a997704ceb92bd9cd99d33952de90c9f5f93c82c62360ae";
    private String txHash = "488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d";
    private String txKey = "6c336e52ed537676968ee319af6983c80b869ca6a732b5962c02748b486f8f0f";
    private XmrTxProofParser parser;
    private Date tradeDate;

    @Before
    public void prepareMocksAndObjects() {
        long amount = 100000000000L;
        tradeDate = new Date(1574922644000L);
        String serviceAddress = "127.0.0.1:8081";
        AutoConfirmSettings autoConfirmSettings = new AutoConfirmSettings(true,
                10,
                1,
                Collections.singletonList(serviceAddress),
                "XMR");

        // TODO using the mocking framework would be better...
        String recipientAddress = "4ATyxmFGU7h3EWu5kYR6gy6iCNFCftbsjATfbuBBjsRHJM4KTwEyeiyVNNUmsfpK1kdRxs8QoPLsZanGqe1Mby43LeyWNMF";
        xmrTxProofModel = new XmrTxProofModel(
                "dummyTest",
                txHash,
                txKey,
                recipientAddress,
                amount,
                tradeDate,
                autoConfirmSettings);

        parser = new XmrTxProofParser();
    }

    @Test
    public void testJsonRoot() {
        // checking what happens when bad input is provided
        assertSame(parser.parse(xmrTxProofModel,
                "invalid json data").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse(xmrTxProofModel,
                "").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse(xmrTxProofModel,
                "[]").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse(xmrTxProofModel,
                "{}").getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }

    @Test
    public void testJsonTopLevel() {
        // testing the top level fields: data and status
        assertSame(parser.parse(xmrTxProofModel,
                "{'data':{'title':''},'status':'fail'}")
                .getDetail(), XmrTxProofRequest.Detail.TX_NOT_FOUND);
        assertSame(parser.parse(xmrTxProofModel,
                "{'data':{'title':''},'missingstatus':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse(xmrTxProofModel,
                "{'missingdata':{'title':''},'status':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }

    @Test
    public void testJsonAddress() {
        assertSame(parser.parse(xmrTxProofModel,
                "{'data':{'missingaddress':'irrelevant'},'status':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.API_INVALID);
        assertSame(parser.parse(xmrTxProofModel,
                "{'data':{'address':'e957dac7'},'status':'success'}")
                .getDetail(), XmrTxProofRequest.Detail.ADDRESS_INVALID);
    }

    @Test
    public void testJsonTxHash() {
        String missing_tx_hash = "{'data':{'address':'" + recipientAddressHex + "'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, missing_tx_hash).getDetail(), XmrTxProofRequest.Detail.API_INVALID);

        String invalid_tx_hash = "{'data':{'address':'" + recipientAddressHex + "', 'tx_hash':'488e48'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, invalid_tx_hash).getDetail(), XmrTxProofRequest.Detail.TX_HASH_INVALID);
    }

    @Test
    public void testJsonTxKey() {
        String missing_tx_key = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, missing_tx_key).getDetail(), XmrTxProofRequest.Detail.API_INVALID);

        String invalid_tx_key = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'cdce04'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, invalid_tx_key).getDetail(), XmrTxProofRequest.Detail.TX_KEY_INVALID);
    }

    @Test
    public void testJsonTxTimestamp() {
        String missing_tx_timestamp = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "'," +
                "'viewkey':'" + txKey + "'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, missing_tx_timestamp).getDetail(), XmrTxProofRequest.Detail.API_INVALID);

        String invalid_tx_timestamp = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "'," +
                "'tx_timestamp':'12345'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, invalid_tx_timestamp).getDetail(), XmrTxProofRequest.Detail.TRADE_DATE_NOT_MATCHING);

        long tradeTimeSec = tradeDate.getTime() / 1000;
        String ts = String.valueOf(tradeTimeSec - MAX_DATE_TOLERANCE - 1);
        String invalid_tx_timestamp_1ms_too_old = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "'," +
                "'tx_timestamp':'" + ts + "'}, 'status':'success'}";
        assertSame(parser.parse(xmrTxProofModel, invalid_tx_timestamp_1ms_too_old).getDetail(), XmrTxProofRequest.Detail.TRADE_DATE_NOT_MATCHING);

        ts = String.valueOf(tradeTimeSec - MAX_DATE_TOLERANCE);
        String valid_tx_timestamp_exact_MAX_DATE_TOLERANCE = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "'," +
                "'tx_timestamp':'" + ts + "'}, 'status':'success'}";
        parser.parse(xmrTxProofModel, valid_tx_timestamp_exact_MAX_DATE_TOLERANCE);
        assertNotSame(parser.parse(xmrTxProofModel, valid_tx_timestamp_exact_MAX_DATE_TOLERANCE).getDetail(), XmrTxProofRequest.Detail.TRADE_DATE_NOT_MATCHING);

        ts = String.valueOf(tradeTimeSec - MAX_DATE_TOLERANCE + 1);
        String valid_tx_timestamp_less_than_MAX_DATE_TOLERANCE = "{'data':{'address':'" + recipientAddressHex + "', " +
                "'tx_hash':'" + txHash + "', " +
                "'viewkey':'" + txKey + "'," +
                "'tx_timestamp':'" + ts + "'}, 'status':'success'}";
        assertNotSame(parser.parse(xmrTxProofModel, valid_tx_timestamp_less_than_MAX_DATE_TOLERANCE).getDetail(), XmrTxProofRequest.Detail.TRADE_DATE_NOT_MATCHING);
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
                "'tx_timestamp':'" + epochDate + "'}" +
                "}";
        assertSame(parser.parse(xmrTxProofModel, json), XmrTxProofRequest.Result.SUCCESS);
        json = json.replaceFirst("777", "0");

        assertSame(parser.parse(xmrTxProofModel, json).getDetail(), XmrTxProofRequest.Detail.PENDING_CONFIRMATIONS);

        json = json.replaceFirst("100000000000", "100000000001");
        assertSame(parser.parse(xmrTxProofModel, json).getDetail(), XmrTxProofRequest.Detail.AMOUNT_NOT_MATCHING);

        // Revert change of amount
        json = json.replaceFirst("100000000001", "100000000000");
        json = json.replaceFirst("'match':true", "'match':false");
        assertSame(parser.parse(xmrTxProofModel, json).getDetail(), XmrTxProofRequest.Detail.NO_MATCH_FOUND);
    }

    @Test
    public void testJsonFail() {
        String failedJson = "{\"data\":null,\"message\":\"Cant parse tx hash: a\",\"status\":\"error\"}";
        assertSame(parser.parse(xmrTxProofModel, failedJson).getDetail(), XmrTxProofRequest.Detail.API_INVALID);
    }
}
