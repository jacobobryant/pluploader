package com.jacobobryant.musicrecommender;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MyCoolQueue {
    private static RequestQueue queue;

    public static synchronized RequestQueue get(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context.getApplicationContext(),
                    new MyCoolHurlStack(makeFactory(context)));
        }
        return queue;
    }

    private static SSLSocketFactory makeFactory(Context context) {
        try {
            // Load CAs from an InputStream
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = new BufferedInputStream(
                    context.getAssets().open("jacobobryant.com.crt"));

            Certificate ca;
            try {
                ca = cf.generateCertificate(caInput);
            } finally {
                caInput.close();
            }

            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, tmf.getTrustManagers(), null);

            //// Get an instance of the Bouncy Castle KeyStore format
            //KeyStore trusted = KeyStore.getInstance("BKS");
            //// Get the raw resource, which contains the keystore with
            //// your trusted certificates (root and any intermediate certs)
            //InputStream in = mCtx.getApplicationContext().getResources().openRawResource(R.raw.codeprojectssl);
            //try {
            //    // Initialize the keystore with the provided trusted certificates
            //    // Provide the password of the keystore
            //    trusted.load(in, KEYSTORE_PASSWORD);
            //} finally {
            //    in.close();
            //}

            //String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            //TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            //tmf.init(trusted);

            //SSLContext context = SSLContext.getInstance("TLS");
            //context.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory sf = sslcontext.getSocketFactory();
            return sf;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class MyCoolHurlStack extends HurlStack {

        public MyCoolHurlStack(SSLSocketFactory factory) {
            super(null, factory);
        }

        @Override
        protected HttpURLConnection createConnection(URL url) throws IOException {
            HttpsURLConnection conn = (HttpsURLConnection) super.createConnection(url);
            conn.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    // hee hee hee
                    return true;
                }
            });
            return conn;
        }
    }
}
