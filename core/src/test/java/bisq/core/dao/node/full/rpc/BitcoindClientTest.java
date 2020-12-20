package bisq.core.dao.node.full.rpc;

import bisq.network.http.HttpException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import com.googlecode.jsonrpc4j.JsonRpcClientException;
import com.googlecode.jsonrpc4j.RequestIDGenerator;
import kotlin.text.Charsets;

public class BitcoindClientTest {
    private static final String TEST_BLOCK_HASH = "015f37a20d517645a11a6cdd316049f41bc77b4a4057b2dd092114b78147f42c";
    private static final String TEST_BLOCK_VERBOSITY_0 = readFromResourcesUnPrettified("getblock-result-verbosity-0.txt");
    private static final String TEST_BLOCK_VERBOSITY_1 = readFromResourcesUnPrettified("getblock-result-verbosity-1.json");
    private static final String TEST_BLOCK_VERBOSITY_2 = readFromResourcesUnPrettified("getblock-result-verbosity-2.json");

    private BitcoindClient client;
    private int mockResponseCode = 200;
    private boolean canConnect = true;
    private ByteArrayInputStream mockResponse;
    private ByteArrayInputStream mockErrorResponse;
    private ByteArrayOutputStream mockOutputStream = new ByteArrayOutputStream();

    @Before
    public void setUp() throws Exception {
        var mockURLStreamHandler = mock(MyURLStreamHandler.class);
        var mockRequestIDGenerator = mock(RequestIDGenerator.class);

        client = BitcoindClient.builder()
                .rpcHost("127.0.0.1")
                .rpcPort(18443)
                .rpcUser("bisqdao")
                .rpcPassword("bsq")
                .urlStreamHandler(mockURLStreamHandler)
                .requestIDGenerator(mockRequestIDGenerator)
                .build();

        when(mockURLStreamHandler.openConnection(any(), any())).then(inv -> {
            var connection = mock(HttpURLConnection.class);
            if (canConnect) {
                when(connection.getOutputStream()).thenReturn(mockOutputStream);
                if (mockResponseCode < 400) {
                    when(connection.getInputStream()).thenReturn(mockResponse);
                } else {
                    when(connection.getInputStream()).thenThrow(IOException.class);
                    when(connection.getErrorStream()).thenReturn(mockErrorResponse);
                }
            } else {
                doThrow(ConnectException.class).when(connection).connect();
            }
            return connection;
        });
        when(mockRequestIDGenerator.generateID()).thenReturn("987654321");
    }

    @Test
    public void testGetBlockCount() throws Exception {
        var expectedRequest = toJson("{'id':'987654321','jsonrpc':'2.0','method':'getblockcount'}");
        mockResponse = toJsonIS("{'result':'150','error':null,'id':'123456789'}");

        assertEquals((Integer) 150, client.getBlockCount());
        assertEquals(expectedRequest, mockOutputStream.toString(UTF_8));
    }

    @Test(expected = ConnectException.class)
    public void testGetBlockCount_noConnection() throws Exception {
        canConnect = false;

        client.getBlockCount();
    }

    @Test(expected = HttpException.class)
    public void testGetBlockCount_wrongCredentials() throws Exception {
        mockResponseCode = 401;
//        mockResponseCustomHeaders.put("WWW-Authenticate", "[Basic realm=\"jsonrpc\"]");

        client.getBlockCount();
    }

    @Test
    public void testGetBlockHash() throws Exception {
        var expectedRequest = toJson("{'id':'987654321','jsonrpc':'2.0','method':'getblockhash','params':[139]}");
        mockResponse = toJsonIS("{'result':'" + TEST_BLOCK_HASH + "','error':null,'id':'123456789'}");

        assertEquals(TEST_BLOCK_HASH, client.getBlockHash(139));
        assertEquals(expectedRequest, mockOutputStream.toString(UTF_8));
    }

    @Test(expected = JsonRpcClientException.class)
    public void testGetBlockHash_heightOutOfRange() throws Exception {
        mockResponseCode = 500;
        mockErrorResponse = toJsonIS("{'result':null,'error':{'code':-8,'message':'Block height out of range'},'id':'123456789'}");

        client.getBlockHash(151);
    }

    @Test
    @Ignore // TODO: Allow serialization/deserialization between RawBlock (with special field) and Json string
    public void testGetBlock_verbosity_0() throws Exception {
        doTestGetBlock(0, "\"" + TEST_BLOCK_VERBOSITY_0 + "\"");
    }

    @Test
    @Ignore // TODO: Allow serialization/deserialization between RawTransaction (with special field) and Json string
    public void testGetBlock_verbosity_1() throws Exception {
        doTestGetBlock(1, TEST_BLOCK_VERBOSITY_1);
    }

    @Test
    public void testGetBlock_verbosity_2() throws Exception {
        doTestGetBlock(2, TEST_BLOCK_VERBOSITY_2);
    }

    private void doTestGetBlock(int verbosity, String blockJson) throws Exception {
        var expectedRequest = toJson("{'id':'987654321','jsonrpc':'2.0','method':'getblock','params':['"
                + TEST_BLOCK_HASH + "'," + verbosity + "]}");
        mockResponse = toJsonIS("{'result':" + blockJson + ",'error':null,'id':'123456789'}");

        var block = client.getBlock(TEST_BLOCK_HASH, verbosity);
        var blockJsonRoundTripped = new ObjectMapper().writeValueAsString(block);

        assertEquals(blockJson, blockJsonRoundTripped);
        assertEquals(expectedRequest, mockOutputStream.toString(UTF_8));
    }

    @Test(expected = JsonRpcClientException.class)
    public void testGetBlock_blockNotFound() throws Exception {
        mockResponseCode = 500;
        mockErrorResponse = toJsonIS("{'result':null,'error':{'code':-5,'message':'Block not found'},'id':'123456789'}");

        client.getBlock(TEST_BLOCK_HASH.replace('f', 'e'), 2);
    }

    @Test(expected = JsonRpcClientException.class)
    public void testGetBlock_malformedHash() throws Exception {
        mockResponseCode = 500;
        mockErrorResponse = toJsonIS("{'result':null,'error':{'code':-8,'message':'blockhash must be of length 64 " +
                "(not 3, for \\'foo\\')'},'id':'123456789'}");

        client.getBlock("foo", 2);
    }

    private static String toJson(String json) {
        return json.replace("'", "\"").replace("\\\"", "'");
    }

    private static ByteArrayInputStream toJsonIS(String json) {
        return new ByteArrayInputStream(toJson(json).getBytes(UTF_8));
    }

    private static String readFromResourcesUnPrettified(String resourceName) {
        try {
            var path = Paths.get(BitcoindClientTest.class.getResource(resourceName).toURI());
            return new String(Files.readAllBytes(path), Charsets.UTF_8).replaceAll("(\\s+\\B|\\B\\s+|\\v)", "");
        } catch (Exception e) {
            return null;
        }
    }

    private static abstract class MyURLStreamHandler extends URLStreamHandler {
        @Override
        public abstract URLConnection openConnection(URL u, Proxy p);
    }
}
