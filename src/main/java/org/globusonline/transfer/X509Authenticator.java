package org.globusonline.transfer;

import javax.net.ssl.HttpsURLConnection;

public class X509Authenticator implements Authenticator {

	private String username;
	
	public X509Authenticator(String username){
		this.username = username;
	}

	public void authenticateConnection(HttpsURLConnection c) {
        c.setRequestProperty("X-Transfer-API-X509-User", this.username);
	}

}
