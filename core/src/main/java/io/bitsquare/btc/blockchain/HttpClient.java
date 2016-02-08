package io.bitsquare.btc.blockchain;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

// TODO route over tor
public class HttpClient implements Serializable {

    private String baseUrl;

    public HttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String requestWithGET(String param) throws IOException, HttpException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + param);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);

            if (connection.getResponseCode() == 200) {
                return convertInputStreamToString(connection.getInputStream());
            } else {
                connection.getErrorStream().close();
                throw new HttpException(convertInputStreamToString(connection.getErrorStream()));
            }
        } finally {
            if (connection != null)
                connection.getInputStream().close();
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "HttpClient{" +
                "baseUrl='" + baseUrl + '\'' +
                '}';
    }
}
