package jenkins.plugins.mdtdeploy.util;

/**
 * Created by rgroult on 13/05/16.
 */
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Defines the class CustomSSLFactory.
 */
public class SSLSocketFactoryManager {

    public static SSLSocketFactory createDefaultSslSocketFactory() {
        TrustManager[] byPassTrustManagers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
        } };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, byPassTrustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        }catch (Exception e){
            System.out.println("Exception : "+e.toString());
        }
        return null;
    }

    public static HostnameVerifier createNullHostnameVerifier(){
        return new NullHostNameVerifier();
    }
    static public class NullHostNameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}