package com.eggwall.android.photoviewer;

import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Charsets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class CryptoRoutines {
    private static final String TAG = "CryptoRoutines";

    /**
     * Decrypt a byte array using AES with CBC, PKC5_PADDING.
     *
     * @param cipherText the text to decode
     * @param iv the initialization vector
     * @param key the secret key
     * @return the decoded text.
     * @throws Exception
     */
    public static byte[] decrypt(byte[] cipherText, byte[] iv, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(FileController.AES_CBC_PKCS5_PADDING);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivspec);
        byte[] plainText = cipher.doFinal(cipherText);
        Log.d(TAG, "plainText: " + bToS(plainText) + ", cipherText: " + bToS(cipherText));
        return plainText;
    }

    /**
     * Test routine to encrypt a string. This will return both the encrypted text and the IV that
     * you need to feed back to the decrypt routine.
     *
     * This uses AES with CBC, PKC5_PADDING making it the inverse operation of
     * {@link CryptoRoutines#decrypt(byte[], byte[], SecretKey)}
     * @param plainText the text to encrypt.
     * @param key the secret key.
     * @return a Pair containing the cipherText, and the Initialization Vector
     * @throws Exception
     */
    public static Pair<byte[],byte[]> encrypt(byte[] plainText, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(FileController.AES_CBC_PKCS5_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = cipher.doFinal(plainText);
        key.getEncoded();
        byte[] iv = cipher.getIV();
        Log.d(TAG, "plainText: " + bToS(plainText) + ", cipherText: " + bToS(cipherText));
        Log.d(TAG, "IV: " + bToS(iv));
        Pair<byte[], byte[] > m = new Pair<>(cipherText, iv);
        return m;
    }

    /**
     * Decrypt a file.
     *
     * @param cipherPath the path of the file to read. This file must be readable
     * @param iv the initialization vector to use to decrypt
     * @param key the secret key used to decrypt
     * @param plainPath the location where the decrypted file is to be placed. This location must
     *                  be writable
     * @return true if the operation succeeded
     * @throws Exception
     */
    public static boolean decrypt(String cipherPath, byte[] iv, SecretKey key, String plainPath)
            throws Exception {
        // Open the input file
        File cipherFile = new File(cipherPath);


        Cipher cipher = Cipher.getInstance(FileController.AES_CBC_PKCS5_PADDING);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivspec);
        FileInputStream fis = new FileInputStream(cipherFile);
        BufferedInputStream bis = new BufferedInputStream(fis);
        CipherInputStream cis = new CipherInputStream(bis, cipher);

        // Create a file to write to.
        File toWrite = new File(plainPath);
        final int fourMegs = 4 * 1024 * 1024;
        byte[] buffer = new byte[fourMegs];
        boolean couldCreate = toWrite.createNewFile();
        if (!couldCreate) {
            Log.d(TAG, "Could not create file " + plainPath);
            return false;
        }
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(toWrite));
        int numBytes;
        while ((numBytes = cis.read(buffer)) > 0) {
            out.write(buffer, 0, numBytes);
        }
        out.close();
        Log.d(TAG, "Wrote plainText: " + plainPath);
        return true;
    }

    /**
     * Encrypt a file.
     *
     * @param plainPath the path of the file to encrypt. This path must be readable.
     * @param key the secret to use to encrypt the file
     * @param cipherPath the location where the encrypted file is to be written. This must be
     *                   writable
     * @return the byte array containing the initialization vector.
     * @throws Exception
     */
    public static byte[] encrypt(String plainPath, SecretKey key, String cipherPath)
            throws Exception {
        Cipher cipher = Cipher.getInstance(FileController.AES_CBC_PKCS5_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        File f = new File(plainPath);
        FileInputStream fis = new FileInputStream(f);

        File toWrite = new File(cipherPath);
        final int fourMegs = 4 * 1024 * 1024;
        byte[] buffer = new byte[fourMegs];

        boolean couldCreate = toWrite.createNewFile();
        if (!couldCreate) {
            Log.d(TAG, "Could not create the new file " + cipherPath);
            return null;
        }
        BufferedInputStream bis = new BufferedInputStream(fis);
        FileOutputStream fos = new FileOutputStream(toWrite);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        CipherOutputStream cos = new CipherOutputStream(bos, cipher);

        int numBytes;
        while ((numBytes = bis.read(buffer)) > 0) {
            Log.d(TAG, "encrypt read " + numBytes + " bytes.");
            cos.write(buffer, 0, numBytes);
        }
        cos.close();
        Log.d(TAG, "Wrote plainText: " + cipherPath);
        return iv;
    }

    /**
     * Base64 encoding of input byte
     * @param in
     * @return
     */
    public static String bToS(byte[] in) {
        return Base64.encodeToString(in, Base64.DEFAULT);
    }

    /**
     * decode a string into its byte.
     * @param in
     * @return
     */
    public static byte[] STob(String in) {
        return Base64.decode(in, Base64.DEFAULT);
    }

    /**
     * Create an AES key for use with the encrypt/decrypt routines.
     * @return an AES SecretKey
     */
    public static SecretKey generateKey() {
        SecretKey key = null;
        try {
            key = KeyGenerator.getInstance("AES").generateKey();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Key creation failed!", e);
        }
        return key;
    }

    /**
     * Create an AES SecretKey using the Base64-encoded string provided here.
     * @param in a Base64 encoded string obtained from {@link CryptoRoutines#generateKey()}
     * @return the Secret key
     */
    public static SecretKey keyFromString(String in) {
        return new SecretKeySpec(STob(in), "AES");
    }


    /**
     * Simple method to test encryption and decryption and show a result to the user.
     * @return true if the test passed
     */
    public static boolean decryptTextTest() {
        // Let's experiment with a given Base64 encoded key.
        String keyHardcode="SOh7N8bl1R5ZoJrGLzhzjA==";

        // And let's try out encrypting and decrypting
        try {
            SecretKey skey = keyFromString(keyHardcode);

            String key = bToS(skey.getEncoded());
            Log.d(TAG, "I generated this crazy long key: " + key);
            String expected = "This is a long message";
            byte[] testMessage = expected.getBytes(Charsets.UTF_8);
            Pair<byte[],byte[]> m = encrypt(testMessage, skey);

            byte[] cipherText = m.first;
            byte[] iv = m.second;
            // Let's print that out, and see what we can do with it.
            Log.d(TAG, "I got cipherText " + bToS(cipherText));

            // And decrypt
            byte[] plainText = decrypt(cipherText, iv, skey);
            // And let's print that out.
            String observed = new String(plainText, Charsets.UTF_8);
            Log.d(TAG, "I got this message back: " + observed);

            if (expected.matches(observed)) {
                Log.d(TAG, "Test PASSED");
                return true;
            } else {
                Log.d(TAG, "Test Failed!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Simple method to test encryption and decryption of a stream and show a result to the user.
     * @return true if the test passed
     */
    public static boolean decryptFileTest() {
        // Let's experiment with a given key.
        String keyHardcode="SOh7N8bl1R5ZoJrGLzhzjA==";


        // And let's try out encrypting and decrypting
        try {
            SecretKey skey = keyFromString(keyHardcode);

            String key = bToS(skey.getEncoded());
            Log.d(TAG, "I generated this crazy long key: " + key);
            String expected = "This is a long message";
            String plainPath = Environment.getExternalStorageDirectory().getPath().concat("/")
                    .concat("plain.txt");
            String cipherPath = Environment.getExternalStorageDirectory().getPath().concat("/")
                    .concat("cipher.txt");
            // First, delete the file.
//            if ((new File(cipherPath)).delete()) {
//                Log.d(TAG, "Old cipher file deleted.");
//            }

            byte[] iv = encrypt(plainPath, skey, cipherPath);
            if (iv == null) {
                Log.d(TAG, "Encryption failed");
                return false;
            }
            Log.d(TAG, "Encryption succeeded. IV = " + bToS(iv));

            // Try to decrypt.
            String testPlainPath = Environment.getExternalStorageDirectory().getPath().concat("/")
                    .concat("test.txt");
            boolean result = decrypt(cipherPath, iv, skey, testPlainPath);
            if (!result) {
                Log.d(TAG, "Decryption failed for  " + testPlainPath);
                return false;
            }
            // TODO: Check here to see if the file has some text in there.
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        // And now delete the file.
        return false;
    }
}
