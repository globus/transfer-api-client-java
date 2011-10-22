/*
 * Copyright 2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globusonline.transfer;

import java.io.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

import java.net.MalformedURLException;
import java.net.URL;

import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONObject;
import org.json.JSONException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;


/**
 * Client which parses JSON response into org.json.JSONObject, from json.org.
 */
public class JSONTransferAPIClient extends BCTransferAPIClient {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println(
                "Usage: java org.globusonline.transfer.JSONTransferAPIClient "
                + "username [path [cafile certfile keyfile [baseurl]]]");
            System.exit(1);
        }
        String username = args[0];

        String path = "/tasksummary";
        if (args.length > 1 && args[1].length() > 0)
            path = args[1];

        String cafile = null;
        if (args.length > 2 && args[2].length() > 0)
            cafile = args[2];

        String certfile = null;
        if (args.length > 3 && args[3].length() > 0)
            certfile = args[3];

        String keyfile = null;
        if (args.length > 4 && args[4].length() > 0)
            keyfile = args[4];

        String baseUrl = null;
        if (args.length > 5 && args[5].length() > 0)
            baseUrl = args[5];

        try {
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                         cafile, certfile, keyfile, baseUrl);
            Result r = c.getResult(path);
            System.out.println(r.statusCode + " " + r.statusMessage);
            System.out.println(r.document.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONTransferAPIClient(String username)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, null, null, null, null);
    }

    public JSONTransferAPIClient(String username, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, null, null, null, baseUrl);
    }

    public JSONTransferAPIClient(String username,
                      String trustedCAFile, String certFile, String keyFile)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, trustedCAFile, certFile, keyFile, null);
    }

    /**
     * Create a client for the user.
     *
     * @param username  the Globus Online user to sign in to the API with.
     * @param trustedCAFile path to a PEM file with a list of certificates
     *                      to trust for verifying the server certificate.
     *                      If null, just use the trust store configured by
     *                      property files and properties passed on the
     *                      command line.
     * @param certFile  path to a PEM file containing a client certificate
     *                  to use for authentication. If null, use the key
     *                  store configured by property files and properties
     *                  passed on the command line.
     * @param keyFile  path to a PEM file containing a client key
     *                 to use for authentication. If null, use the key
     *                 store configured by property files and properties
     *                 passed on the command line.
     * @param baseUrl  alternate base URL of the service; can be used to
     *                 connect to different versions of the API and instances
     *                 running on alternate servers. If null, the URL of
     *                 the latest version running on the production server
     *                 is used.
     */
    public JSONTransferAPIClient(String username,
                                 String trustedCAFile, String certFile,
                                 String keyFile, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        super(username, FORMAT_JSON, trustedCAFile, certFile, keyFile, baseUrl);
    }

    protected APIError constructAPIError(int statusCode, String statusMessage,
                                         String errorCode, InputStream input) {
        APIError error = new APIError(statusCode, statusMessage, errorCode);
        try {
            JSONObject errorDocument = new JSONObject(readString(input));
            error.requestId = errorDocument.getString("request_id");
            error.resource = errorDocument.getString("resource");
            error.code = errorDocument.getString("code");
            error.message = errorDocument.getString("message");
        } catch (Exception e) {
            // Make sure the APIError gets thrown, even if we can't parse out
            // the details. If parsing fails, shove the exception in the
            // message fields, so the parsing error is not silently dropped.
            error.message = e.toString();
        }
        return error;
    }

    public Result getResult(String path)
        throws IOException, MalformedURLException, GeneralSecurityException,
               JSONException, APIError {
        return getResult(path, null);
    }

    public Result getResult(String path, Map<String, String> queryParams)
        throws IOException, MalformedURLException, GeneralSecurityException,
               JSONException, APIError {

        HttpsURLConnection c = request("GET", path, null, queryParams);

        Result result = new Result();
        result.statusCode = c.getResponseCode();
        result.statusMessage = c.getResponseMessage();

        result.document = new JSONObject(readString(c.getInputStream()));

        c.disconnect();

        return result;
    }

    public Result postResult(String path, String data,
                             Map<String, String> queryParams)
        throws IOException, MalformedURLException, GeneralSecurityException,
               JSONException, APIError {

        HttpsURLConnection c = request("POST", path, data, queryParams);

        Result result = new Result();
        result.statusCode = c.getResponseCode();
        result.statusMessage = c.getResponseMessage();

        result.document = new JSONObject(readString(c.getInputStream()));

        c.disconnect();

        return result;
    }


    public static class Result {
        public JSONObject document;
        public int statusCode;
        public String statusMessage;

        public Result() {
            this.document = null;
            this.statusCode = -1;
            this.statusMessage = null;
        }
    }

    public static String readString(InputStream in) throws IOException {
        Reader reader = null;
        try {
            // TODO: add charset
            reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }
}
