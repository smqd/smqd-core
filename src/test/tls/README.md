# HOW-TO SMQD TLS

## SSL

1. generate server keys

```bash
$ cd keygen
$ ./server.keygen.sh
$ ./client.keygen.sh
```

2. add tls key settings in smqd.conf

```
smqd {
  tls {
    storetype = jks
    keystore = src/test/tls/keygen/smqd-server.jks
    storepass = smqd.demo.key
    keypass = smqd.demo.key
  }
}

3. add server address in `/etc/hosts`

because keygen script create keys based default server name as `smqd.thing2x.com`

```
smqd.thing2x.com 127.0.0.1
```

4. run client examples

requires paho-mqtt

```bash
$ pip install paho-mqtt
```

- Server certificate only

    - [Mqtt client with oneway SSL](mqtt-client-ssl-oneway.py)

- X.509 Certificate based client authentication

    - [Mqtt client with twoway SSL](mqtt-client-ssl-twoway.py)


## Mqtt SSL Client examples


reference:
https://thingsboard.io/docs/user-guide/mqtt-over-ssl/
https://thingsboard.io/docs/user-guide/access-token/
https://thingsboard.io/docs/user-guide/certificates/


```java
import com.google.common.io.Resources;
import org.eclipse.paho.client.mqttv3.*;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

public class MqttSslClient {

    private static final String MQTT_URL = "ssl://localhost:8883";

    private static final String clientId = "MQTT_SSL_JAVA_CLIENT";
    private static final String keyStoreFile = "mqttclient.jks";
    private static final String JKS="JKS";
    private static final String TLS="TLS";
    private static final String CLIENT_KEYSTORE_PASSWORD = "password";
    private static final String CLIENT_KEY_PASSWORD = "password";

    public static void main(String[] args) {

        try {

            URL ksUrl = Resources.getResource(keyStoreFile);
            File ksFile = new File(ksUrl.toURI());
            URL tsUrl = Resources.getResource(keyStoreFile);
            File tsFile = new File(tsUrl.toURI());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            KeyStore trustStore = KeyStore.getInstance(JKS);
            trustStore.load(new FileInputStream(tsFile), CLIENT_KEYSTORE_PASSWORD.toCharArray());
            tmf.init(trustStore);
            KeyStore ks = KeyStore.getInstance(JKS);

            ks.load(new FileInputStream(ksFile), CLIENT_KEYSTORE_PASSWORD.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, CLIENT_KEY_PASSWORD.toCharArray());

            KeyManager[] km = kmf.getKeyManagers();
            TrustManager[] tm = tmf.getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance(TLS);
            sslContext.init(km, tm, null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setSocketFactory(sslContext.getSocketFactory());
            MqttAsyncClient client = new MqttAsyncClient(MQTT_URL, clientId);
            client.connect(options);
            Thread.sleep(3000);
            MqttMessage message = new MqttMessage();
            message.setPayload("{\"key1\":\"value1\", \"key2\":true, \"key3\": 3.0, \"key4\": 4}".getBytes());
            client.publish("v1/devices/me/telemetry", message);
            client.disconnect();
            System.out.println("Disconnected");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```