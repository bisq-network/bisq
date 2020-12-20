package bisq.core.dao.node.full.rpc;

import bisq.core.dao.node.full.rpc.dto.RawBlock;

import bisq.network.http.HttpException;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;

import java.nio.charset.StandardCharsets;

import java.io.IOException;


import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import java.lang.reflect.Type;

import static com.google.common.base.Preconditions.checkNotNull;



import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.JsonRpcMethod;
import com.googlecode.jsonrpc4j.ProxyUtil;
import com.googlecode.jsonrpc4j.RequestIDGenerator;

public interface BitcoindClient {
    @JsonRpcMethod("getblock")
    RawBlock getBlock(String headerHash, int verbosity) throws IOException, HttpException;

    @JsonRpcMethod("getblockcount")
    Integer getBlockCount() throws IOException, HttpException;

    @JsonRpcMethod("getblockhash")
    String getBlockHash(Integer blockHeight) throws IOException, HttpException;

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private String rpcHost;
        private int rpcPort = -1;
        private String rpcUser;
        private String rpcPassword;
        private URLStreamHandler urlStreamHandler;
        private RequestIDGenerator requestIDGenerator;

        public Builder rpcHost(String rpcHost) {
            this.rpcHost = rpcHost;
            return this;
        }

        public Builder rpcPort(int rpcPort) {
            this.rpcPort = rpcPort;
            return this;
        }

        public Builder rpcUser(String rpcUser) {
            this.rpcUser = rpcUser;
            return this;
        }

        public Builder rpcPassword(String rpcPassword) {
            this.rpcPassword = rpcPassword;
            return this;
        }

        public Builder urlStreamHandler(URLStreamHandler urlStreamHandler) {
            this.urlStreamHandler = urlStreamHandler;
            return this;
        }

        public Builder requestIDGenerator(RequestIDGenerator requestIDGenerator) {
            this.requestIDGenerator = requestIDGenerator;
            return this;
        }

        public BitcoindClient build() throws MalformedURLException {
            var userPass = checkNotNull(rpcUser, "rpcUser not set") +
                    ":" + checkNotNull(rpcPassword, "rpcPassword not set");

            var headers = Collections.singletonMap("Authorization", "Basic " +
                    Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.US_ASCII)));

            var httpClient = new JsonRpcHttpClient(
                    new URL("http", rpcHost, rpcPort, "", urlStreamHandler), headers) {
                @Override
                public Object invoke(String methodName,
                                     Object argument,
                                     Type returnType,
                                     Map<String, String> extraHeaders) throws Throwable {
                    try {
                        return super.invoke(methodName, argument, returnType, extraHeaders);
                    } catch (RuntimeException e) {
                        // Convert the following package-private exception into one that we can catch directly,
                        // so that HTTP errors (such as authentication failure) can be handled more gracefully.
                        if (e.getClass().getName().equals("com.googlecode.jsonrpc4j.HttpException")) {
                            throw new HttpException(e.getMessage(), e.getCause());
                        }
                        throw e;
                    }
                }
            };
            Optional.ofNullable(requestIDGenerator).ifPresent(httpClient::setRequestIDGenerator);
            return ProxyUtil.createClientProxy(getClass().getClassLoader(), BitcoindClient.class, httpClient);
        }
    }
}
