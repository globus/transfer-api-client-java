package org.globusonline.transfer;

import java.security.GeneralSecurityException;
import java.util.*;
import java.io.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.json.*;

public class ErrorTest {
    private JSONTransferAPIClient client;
    private static DateFormat isoDateFormat =
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public ErrorTest(JSONTransferAPIClient client) {
        this.client = client;
    }

    public static void main(String args[]) {
        if (args.length < 1) {
            System.err.println(
                "Usage: java org.globusonline.transfer.ErrorTest "
                + "username [cafile certfile keyfile [baseurl]]]");
            System.exit(1);
        }
        String username = args[0];

        String cafile = null;
        if (args.length > 1 && args[1].length() > 0)
            cafile = args[1];

        String certfile = null;
        if (args.length > 2 && args[2].length() > 0)
            certfile = args[2];

        String keyfile = null;
        if (args.length > 3 && args[3].length() > 0)
            keyfile = args[3];

        String baseUrl = null;
        if (args.length > 4 && args[4].length() > 0)
            baseUrl = args[4];

        try {
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                         cafile, certfile, keyfile, baseUrl);
            System.out.println("base url: " + c.getBaseUrl());
            ErrorTest et = new ErrorTest(c);
            et.run();

            // test auth error
            System.out.println("=== 400 Auth Failed ===");
            c = new JSONTransferAPIClient(username,
                                          cafile, null, null, baseUrl);
            try {
                c.getResult("/tasksummary");
            } catch (APIError e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run()
    throws IOException, JSONException, GeneralSecurityException, APIError {
        System.out.println("=== 404 Not Found ===");
        try {
            JSONTransferAPIClient.Result r = client.getResult("/doesnotexist");
        } catch (APIError e) {
            e.printStackTrace();
        }
        System.out.println("=== 400 Bad Request ===");
        try {
            JSONTransferAPIClient.Result r = client.getResult(
                                        "/tasksummary?filter=nosuchfield:1");
        } catch (APIError e) {
            e.printStackTrace();
        }
    }
}
