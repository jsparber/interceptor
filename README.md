# vpnMITM
A packet interceptor for Android built on top of VpnService

Original Progject License
Apache v2.0
My added code is Licensed under
GPLv3

This Project is a work in progress it will contain ugly hacks, grammer and spelling mistakes.



Generate Certificat
===================
Download the latest version of the bks provider from https://www.bouncycastle.org/latest_releases.html

```
keytool -genkey -keyalg RSA -alias selfsigned -storetype BKS -keystore keystore.bks -storepass password -validity 360 -keysize 2048 -ext SAN=DNS:localhost,IP:127.0.0.1  -validity 9999 -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath ./bcprov.jar
```
