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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.bouncycastle.jce.provider.BouncyCastleProvider;


/**
 * Client which parses XML response into DOM documents.
 */
public class XMLDomTransferAPIClient extends BCTransferAPIClient {

    protected DocumentBuilderFactory builderFactory;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println(
                "Usage: java org.globusonline.transfer.XMLDomTransferAPIClient "
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
            XMLDomTransferAPIClient c = new XMLDomTransferAPIClient(username,
                                         cafile, certfile, keyfile, baseUrl);
            Result r = c.getDocument(path);
            System.out.println(r.statusCode + " " + r.statusMessage);

            Element root = r.document.getDocumentElement();

            System.out.println(root.getTagName());
            if (root.getTagName() == "tasksummary") {
                // Using XPath API. Less efficient but more convenient for
                // pulling out specific values in more complex documents.
                // See APIError for another way to pull out specific values,
                // using the Document directly.
                System.out.println("  active: "
                                   + r.xpath("/tasksummary/active/text()",
                                             XPathConstants.NUMBER));

                // Change the context to the tasksummary node, since
                // everything we care about is a descendant.
                Node tasksummaryNode = (Node) r.xpath("/tasksummary",
                                                      XPathConstants.NODE);
                r.setXPathContext(tasksummaryNode);
                System.out.println("  inactive: "
                                   + r.xpath("inactive/text()",
                                             XPathConstants.STRING));

                // the /text() is not necessary. Also STRING is the default.
                System.out.println("  failed: " + r.xpath("failed"));
                System.out.println("  succeeded: " + r.xpath("succeeded"));
            } else {
                // Using DOM API; displays only the first level of children,
                // just as an example.
                Node child = root.getFirstChild();
                Node grandchild = null;
                while (child != null) {
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        System.out.print("  " + child.getNodeName());
                        grandchild = child.getFirstChild();
                        if (grandchild.getNodeType() == Node.TEXT_NODE)
                            System.out.print(": " + grandchild.getNodeValue());
                        System.out.println();
                    }
                    child = child.getNextSibling();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public XMLDomTransferAPIClient(String username)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, null, null, null, null);
    }

    public XMLDomTransferAPIClient(String username, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        this(username, null, null, null, baseUrl);
    }

    public XMLDomTransferAPIClient(String username,
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
    public XMLDomTransferAPIClient(String username,
                                   String trustedCAFile, String certFile,
                                   String keyFile, String baseUrl)
            throws KeyManagementException, NoSuchAlgorithmException {
        super(username, FORMAT_XML, trustedCAFile, certFile, keyFile, baseUrl);
        this.builderFactory = DocumentBuilderFactory.newInstance();
    }

    protected APIError constructAPIError(int statusCode, String statusMessage,
                                         String errorCode, InputStream input) {
        APIError error = new APIError(statusCode, statusMessage, errorCode);
        try {
            DocumentBuilder builder = this.builderFactory.newDocumentBuilder();
            Document errorDocument = builder.parse(input);
            error.parseDocument(errorDocument);
        } catch (Exception e) {
            // Make sure the APIError gets thrown, even if we can't parse out
            // the details. If parsing fails, shove the exception in the
            // message fields, so the parsing error is not silently dropped.
            error.message = e.toString();
        }
        return error;
    }

    public Result getDocument(String path)
        throws IOException, MalformedURLException, GeneralSecurityException,
               SAXException, ParserConfigurationException, APIError {

        HttpsURLConnection c = request("GET", path, null, null);

        Result result = new Result();
        result.statusCode = c.getResponseCode();
        result.statusMessage = c.getResponseMessage();

        DocumentBuilder builder = this.builderFactory.newDocumentBuilder();
        result.document = builder.parse(c.getInputStream());

        c.disconnect();

        return result;
    }

    public static class Result {
        public Document document;
        public int statusCode;
        public String statusMessage;

        XPath xpath = null;
        Object xpathContext = null;

        public Result() {
            this.document = null;
            this.statusCode = -1;
            this.statusMessage = null;
        }

        /**
         * Evaluate an XPath expression in the context of the document
         * object.
         *
         * Potential less efficient than manipulating the DOM directly,
         * but much more convenient.
         */
        public Object xpath(String expression, QName returnType)
                                        throws XPathExpressionException {
            if (xpath == null) {
                xpath = XPathFactory.newInstance().newXPath();
                xpathContext = document;
            }
            return xpath.evaluate(expression, xpathContext, returnType);
        }

        public Object xpath(String expression)
                                        throws XPathExpressionException {

            if (xpath == null) {
                xpath = XPathFactory.newInstance().newXPath();
                xpathContext = document;
            }
            return xpath.evaluate(expression, xpathContext);
        }

        public void setXPathContext(Object context) {
            xpathContext = context;
        }
    }
}
