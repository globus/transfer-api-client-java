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

public class MyproxyActivation {
    private String mkproxyPath;

    public static void main(String args[]) {
        if (args.length < 6) {
            System.err.println(
             "Usage: java org.globusonline.transfer.MyproxyActivation "
             + "globus_username epname myproxy_hostname myproxy_username "
             + "myproxy_password certfile [keyfile [baseurl]]");
            System.exit(1);
        }

        String username = args[0];
        String ep = args[1];
        String myproxyHostname = args[2];
        String myproxyUsername = args[3];
        String myproxyPassphrase = args[4];

        String certfile = args[5];
        String keyfile = null;

        if (args.length > 6 && args[6].length() > 0) {
            keyfile = args[6];
        } else {
            keyfile = certfile;
        }

        String baseUrl = null;
        if (args.length > 7 && args[7].length() > 0)
            baseUrl = args[7];

        try {
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                             null, certfile, keyfile, baseUrl);
            c.endpointDeactivate(ep);
            JSONTransferAPIClient.Result r = MyproxyActivation.myproxyActivate(
                c, ep, myproxyHostname, myproxyUsername, myproxyPassphrase);
            System.out.println("Result code: " + r.document.getString("code"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JSONTransferAPIClient.Result myproxyActivate(
                                                 JSONTransferAPIClient c,
                                                 String endpointName,
                                                 String myproxyHostname,
                                                 String myproxyUsername,
                                                 String myproxyPassphrase)
        throws JSONException, IOException, GeneralSecurityException, APIError,
               InterruptedException {
        String url = c.endpointPath(endpointName) + "/activation_requirements";

        JSONTransferAPIClient.Result r = c.getResult(url);

        // Go through requirements and find the myproxy type, then fill
        // in with the values from the function^Wmethod parameters.
        JSONArray reqsArray = r.document.getJSONArray("DATA");
        for (int i=0; i < reqsArray.length(); i++) {
            JSONObject reqObject = reqsArray.getJSONObject(i);
            if (reqObject.getString("type").equals("myproxy")) {
                String name = reqObject.getString("name");
                if (name.equals("hostname")) {
                    reqObject.put("value", myproxyHostname);
                } else if (name.equals("username")) {
                    reqObject.put("value", myproxyUsername);
                } else if (name.equals("passphrase")) {
                    reqObject.put("value", myproxyPassphrase);
                }
                // optional arguments are 'server_dn', required if the hostname
                // does not match the DN in the server's certificate, and
                // 'lifetime_in_hours', to ask for a specific lifetime rather
                // than accepting the server default.
                // See also:
                //  https://transfer.api.globusonline.org/v0.10/document_type/activation_requirements/example?format=json
            }
        }

        url = c.endpointPath(endpointName) + "/activate";
        r = c.postResult(url, r.document);

        return r;
    }
}
