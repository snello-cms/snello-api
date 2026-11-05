package io.snello.util;


import io.quarkus.logging.Log;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("restriction")
public class PasswordUtils implements Serializable {
    
    private static final long serialVersionUID = 1L;

    public static String createPassword(String pwd) {
        return createPasswordWithHashAlgorithm(pwd, "SHA-256");
    }

    public static String createPasswordWithHashAlgorithm(String pwd, String algorithm) {
        MessageDigest md;
        try {
            // es MD5, SHA-256
            md = MessageDigest.getInstance(algorithm);
            md.update(pwd.getBytes());
            byte[] enc = md.digest();
            String encode = new String(java.util.Base64.getMimeEncoder().encode(enc));
            return encode;
        } catch (NoSuchAlgorithmException e) {
            Log.error(e.getMessage(), e);
            return null;
        }
    }
}
