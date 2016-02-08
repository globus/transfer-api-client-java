Requirements
============

Bouncy Castle
-------------

BCTransferAPIClient (and subclasses) require bouncy castle; download the latest
bcprov jar for your JDK release:

http://www.bouncycastle.org/latest_releases.html

and place it in a lib sub directory. The "ext" version with the IDEA algorithm
is not required. Tested with bouncy castle 1.54 and jdk8.

For example::

    VERSION=jdk15on-154
    mkdir -p lib
    cd lib
    wget http://www.bouncycastle.org/download/bcprov-${VERSION}.jar


Ant or Maven
------------

Required to build the code. Maven can also be used to automatically fetch
dependencies (currently just Bouncy Castle).


Building
========

Ant
---

Run ant in the project root. It will compile the source and create
``build/jar/TransferAPIClient.jar``.


Maven
-----

You can also build this project via maven, as usual, just run::

    mvn clean install


Example Run
===========

The example programs exercises various parts of the API including
transferring a dot file between the tutorial endpoints and creating and then
deleting an endpoint. All users have access to the tutorial endpoints
(subject to a small quota), so it should work for any GO user.

New clients should use GoAuth for authentication, X.509 authentication is
deprecated and will be removed in the near future.

GoAuth
------

GoAuth access tokens must be acquired from the Globus Nexus API. For
testing, the simplest method is to use a command line tool like curl
or wget. Here's an example using curl, which will prompt for your
GO password::

    GO_USERNAME="..."

    curl --user $GO_USERNAME 'https://nexus.api.globusonline.org/goauth/token?grant_type=client_credentials'

The Nexus sample library also has a method for getting client credentials:
https://github.com/globusonline/java-nexus-client
see ``org.globusonline.nexus.GoauthClient.getClientOnlyAccessToken``.

The result is JSON::

    {"scopes": ["https://transfer.api.globusonline.org"],
     "access_token_hash": "7363...",
     "issued_on": 1355175114,
     "expiry": 1386711114, 
     "token_type": "Bearer",
     "client_id": "GO_USERNAME",
     "lifetime": 31536000,
     "access_token": "un=GO_USERNAME|tokenid=...",
     "expires_in": 31536000,
     "token_id": "04a117a...",
     "user_name": "GO_USERNAME"}

What you need to pass to the Java Transfer client is just the value of the
``access_token`` field. Note that the access token format my change in the
future, so it should be treated as an opaque string.

The access token obtained this way (``client_credentials`` option) has a long
lifetime, and must be kept private just like your password. One method is to
store it in a text file with read/write permission only by the owner (or better
yet in an encrypted filesystem or subdirectory). Care should also be taken to
avoid putting it in your shell history as well. For example in bash::

    ACCESS_TOKEN=$(cat /path/to/access_token_text_file)
    GO_USERNAME="..."

    java -cp lib/bcprov-jdk15on-154.jar:build/jar/TransferAPIClient.jar \
        org.globusonline.transfer.GoauthExample $GO_USERNAME \
        "$ACCESS_TOKEN"

History will store the commands before variable and subcommand expansion, so
it will not include the token value.

X509 Authentication (DEPRECATED)
-------------------

Using a certificate fetched with myproxy-logon for local user with id 1000::

    USER_CERT=/tmp/x509up_u1000
    USER_KEY=/tmp/x509up_u1000
    GO_USERNAME="..."

    java -cp lib/bcprov-jdk15on-154.jar:build/jar/TransferAPIClient.jar \
        org.globusonline.transfer.Example $GO_USERNAME "$USER_CERT" "$USER_KEY"

For this to work the certificate must be signed by a trusted grid CA and
associated with your GO account.


Changlog
========

0.10.9
------
- Update for Bouncy Castle 1.54
- Deprecate X.509 auth
- Update examples
- Use endpoint ids instead of endpoint canonical_name in URL args
- Remove use of deprecated endpoint_list API

0.10.8
------

- Add InCommon CA and simplify CA handling
- Remove CA file arg from Example and GoauthExample

0.10.7
------

- Add CA files as resource
- Support delegate_proxy activation
- Add some high level methods to JSON client
- Fix for thread-safety issue
- Remove unmaintained XML support
- Add goauth support
