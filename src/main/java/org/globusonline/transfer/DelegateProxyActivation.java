/*
 * Copyright 2012 University of Chicago
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

import java.lang.ProcessBuilder;
import java.io.*;
import java.security.GeneralSecurityException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class DelegateProxyActivation {
    public static final String MKPROXY_PATH = "/home/bda/bin/mkproxy";

    public static void main(String args[]) {
        if (args.length < 1) {
            System.err.println(
             "Usage: java org.globusonline.transfer.DelegateProxyActivation "
             + "username epname [cafile certfile keyfile [baseurl]]]");
            System.exit(1);
        }
        String username = args[0];
        String ep = args[1];
        String credPath = args[2];

        String cafile = null;
        if (args.length > 3 && args[3].length() > 0)
            cafile = args[3];

        String certfile = null;
        if (args.length > 4 && args[4].length() > 0)
            certfile = args[4];

        String keyfile = null;
        if (args.length > 5 && args[5].length() > 0)
            keyfile = args[5];

        String baseUrl = null;
        if (args.length > 6 && args[6].length() > 0)
            baseUrl = args[6];

        try {
            String cred = readEntireFile(credPath);
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                         cafile, certfile, keyfile, baseUrl);
            String url = c.endpointPath(ep) + "/deactivate";
            c.postResult(url, null);
            System.out.println("base url: " + c.getBaseUrl());
            activate(c, ep, cred, 12);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean activate(JSONTransferAPIClient c, String endpointName,
                                   String cred, int hours)
        throws JSONException, IOException, GeneralSecurityException, APIError,
               InterruptedException {
        String url = c.endpointPath(endpointName) + "/activation_requirements";

        JSONTransferAPIClient.Result r = c.getResult(url);

        JSONArray reqsArray = r.document.getJSONArray("DATA");
        String publicKey = null;
        JSONObject proxyChainObject = null;
        for (int i=0; i < reqsArray.length(); i++) {
            JSONObject reqObject = reqsArray.getJSONObject(i);
            System.out.println("  " + reqObject.getString("name"));
            if (reqObject.getString("type").equals("delegate_proxy")) {
                String name = reqObject.getString("name");
                if (name.equals("public_key")) {
                    publicKey = reqObject.getString("value");
                } else if (name.equals("proxy_chain")) {
                    proxyChainObject = reqObject;
                }
            }
        }
        
        if (publicKey == null || proxyChainObject == null) {
            // TODO: raise appropriate exception indicating that ep does
            // not support delegate_proxy activation.
            return false;
        }
        System.out.println("public key: " + publicKey);

        String certChain = createProxyCertificate(MKPROXY_PATH,
                                                  publicKey, cred, hours);

        System.out.println("cert chain: " + certChain);

        proxyChainObject.put("value", certChain);

        url = c.endpointPath(endpointName) + "/activate";
        r = c.postResult(url, r.document);

        System.out.println("" + r.document);
        return true;
    }

    /**
     * Create a proxy certificate using the provided public key and signed
     * by the provided credential.
     *
     * Appends the certificate chain to proxy certificate, and returns as PEM.
     * Uses an external program to construct the certificate; see
     *   https://github.com/globusonline/transfer-api-client-python/tree/master/mkproxy
     * @param mkproxyPath   Absolute path of mkproxy program.
     * @param publicKeyPem  String containing a PEM encoded RSA public key
     * @param credentialPem String containing a PEM encoded credential, with
     *                      certificate, private key, and trust chain.
     * @param hours         Hours the certificate will be valid for.
     */
    public static String createProxyCertificate(String mkproxyPath,
                                                String publicKeyPem,
                                                String credentialPem,
                                                int hours)
        throws IOException, InterruptedException {
        Process p = new ProcessBuilder(mkproxyPath, "" + hours).start();

        DataOutputStream out = new DataOutputStream(p.getOutputStream());
        out.writeBytes(publicKeyPem);
        out.writeBytes(credentialPem);
        out.close();

        p.waitFor();
        InputStreamReader in = new InputStreamReader(p.getInputStream());
        String certChain = readEntireStream(in);
        return certChain;
    }

    private static String readEntireFile(String filename) throws IOException {
        FileReader in = new FileReader(filename);
        return readEntireStream(in);
    }

    private static String readEntireStream(InputStreamReader in)
                                                    throws IOException {
        StringBuilder contents = new StringBuilder();
        char[] buffer = new char[4096];
        int read = 0;
        do {
            contents.append(buffer, 0, read);
            read = in.read(buffer);
        } while (read >= 0);
        return contents.toString();
    }
}
