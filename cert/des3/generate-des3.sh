#! /bin/bash

# https://stackoverflow.com/questions/53047940/grpc-java-set-up-sslcontext-on-server
#
# https://grpc.io/docs/guides/auth
#
# To enable TLS on a server, a certificate chain and private key need to be specified in PEM format. 
# Such private key should not be using a password. The order of certificates in the chain matters: 
# more specifically, the certificate at the top has to be the host CA, while the one at the very 
# bottom has to be the root CA. The standard TLS port is 443, but we use 8443 below to avoid 
# needing extra permissions from the OS.
#
# If the issuing certificate authority is not known to the client then a properly configured 
# SslContext or SSLSocketFactory should be provided to the NettyChannelBuilder or OkHttpChannelBuilder, 
# respectively.

# Set-up SSL on my GRPC server: a certificate chain and a pkcs8 private key

# Generate CA key:

openssl genrsa -des3 -out ca.key 4096

# Generate CA certificate:

# Common Name (e.g. server FQDN or YOUR name) []:localhost
openssl req -new -x509 -days 365 -key ca.key -out ca.crt

Check   cert
openssl x509 -in  ca.crt -text -noout


# Generate server key:

openssl genrsa -des3 -out server.key 4096


# Generate server signing request:

openssl req -new -key server.key -out server.csr
# Common Name (e.g. server FQDN or YOUR name) []:localhost


# Self-sign server certificate:

openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -set_serial 01 -out server.crt


# Remove passphrase from the server key:

openssl rsa -in server.key -out server.key

# Convert to pkcs8

openssl pkcs8 -topk8 -nocrypt -in server.key -out pkcs8_key.pem

#  -dname "cn=bisq.network, ou=None, L=YYY, ST=TTTT, o=ExampleOrg, c=AT
# Common Name (e.g. server FQDN or YOUR name) []:localhost
