package com.cocode.vcode.ide.git.core;

import android.content.Context;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

/**
 * Manages SSH keypair generation for Git operations.
 * Uses only java.security — no external JSch or SSHD dependency needed for generation.
 * <p>
 * Provides JGit SSH transport registration via JSch.
 */
public class SshKeyManager {

    public static File getSshDir(Context context) {
        File sshDir = new File(context.getFilesDir(), ".ssh");
        if (!sshDir.exists()) sshDir.mkdirs();
        return sshDir;
    }

    public static File getPrivateKeyFile(Context context) {
        return new File(getSshDir(context), "id_rsa");
    }

    public static File getPublicKeyFile(Context context) {
        return new File(getSshDir(context), "id_rsa.pub");
    }

    public static boolean hasKeys(Context context) {
        return getPrivateKeyFile(context).exists() && getPublicKeyFile(context).exists();
    }

    public static TransportConfigCallback getTransportConfigCallback(Context context) {
        return new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                if (transport instanceof SshTransport) {
                    SshTransport sshTransport = (SshTransport) transport;
                    sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
                        @Override
                        protected void configure(OpenSshConfig.Host host, Session session) {
                            session.setConfig("StrictHostKeyChecking", "no");
                        }

                        @Override
                        protected JSch createDefaultJSch(FS fs) throws JSchException {
                            JSch defaultJSch = super.createDefaultJSch(fs);
                            defaultJSch.removeAllIdentity();
                            File privateKey = getPrivateKeyFile(context);
                            if (privateKey.exists()) {
                                defaultJSch.addIdentity(privateKey.getAbsolutePath());
                            }
                            return defaultJSch;
                        }
                    });
                }
            }
        };
    }

    /**
     * Generates a 2048-bit RSA keypair and writes PEM private key + OpenSSH public key to disk.
     */
    public static void generateKeys(Context context) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Write private key in PKCS8 PEM format
        byte[] privBytes = kp.getPrivate().getEncoded();
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + android.util.Base64.encodeToString(privBytes, android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING)
                + "\n-----END PRIVATE KEY-----\n";
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(getPrivateKeyFile(context)), StandardCharsets.UTF_8)) {
            w.write(privPem);
        }

        // Write public key in OpenSSH authorized_keys format
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        byte[] pubEncoded = buildOpenSshPublicKey(pub);
        String pubLine = "ssh-rsa " + android.util.Base64.encodeToString(pubEncoded, android.util.Base64.NO_WRAP) + " vcode@vcode\n";
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(getPublicKeyFile(context)), StandardCharsets.UTF_8)) {
            w.write(pubLine);
        }
    }

    /**
     * Reads the stored public key string for display/copy to GitHub.
     */
    public static String readPublicKey(Context context) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(getPublicKeyFile(context)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    // SSH wire-format helpers

    private static byte[] buildOpenSshPublicKey(RSAPublicKey pub) throws Exception {
        byte[] type = "ssh-rsa".getBytes(StandardCharsets.UTF_8);
        byte[] e = pub.getPublicExponent().toByteArray();
        byte[] n = pub.getModulus().toByteArray();
        int totalLen = 4 + type.length + 4 + e.length + 4 + n.length;
        byte[] buf = new byte[totalLen];
        int pos = 0;
        pos = writeInt(buf, pos, type.length);
        System.arraycopy(type, 0, buf, pos, type.length);
        pos += type.length;
        pos = writeInt(buf, pos, e.length);
        System.arraycopy(e, 0, buf, pos, e.length);
        pos += e.length;
        pos = writeInt(buf, pos, n.length);
        System.arraycopy(n, 0, buf, pos, n.length);
        return buf;
    }

    private static int writeInt(byte[] buf, int pos, int val) {
        buf[pos] = (byte) (val >> 24);
        buf[pos + 1] = (byte) (val >> 16);
        buf[pos + 2] = (byte) (val >> 8);
        buf[pos + 3] = (byte) val;
        return pos + 4;
    }
}
