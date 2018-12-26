package com.eggwall.android.photoviewer;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.eggwall.android.photoviewer.data.Album;
import com.eggwall.android.photoviewer.data.AlbumDao;
import com.eggwall.android.photoviewer.data.AlbumDatabase;
import com.google.common.base.Charsets;

import java.io.File;
import java.util.ArrayList;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/*
 * TODO: Unzip a file.
 * TODO: Allow traversing existing file structure.
 * TODO: Delete oldest file: LRU cache.
 */

/**
 * Create a Photo viewer by default.  This screen should show an image by default, and allow the
 * user to change them using the navigation bar.
 */
public class MainActivity extends AppCompatActivity {
    boolean keepScreenOn = true;
    public static final String TAG = "MainActivity";

    UiController uiController;

    int RC_PERMISSION_WRITE_EXTERNAL_STORAGE = 999;
    // Temporarily, all permissions on creation
    private void requestWriteExternalStoragePermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Inform and request")
                    .setMessage("You need to enable permissions, bla bla bla")
                    .setPositiveButton(R.string.common_google_play_services_enable_text, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new
                                    String[]{Manifest
                                    .permission.WRITE_EXTERNAL_STORAGE}, RC_PERMISSION_WRITE_EXTERNAL_STORAGE);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission
                    .WRITE_EXTERNAL_STORAGE}, RC_PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    // Temporarily, all permissions on creation
    private void requestReadExternalStoragePermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Inform and request")
                    .setMessage("You need to enable permissions, bla bla bla")
                    .setPositiveButton(R.string.common_google_play_services_enable_text, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new
                                    String[]{Manifest
                                    .permission.READ_EXTERNAL_STORAGE}, RC_PERMISSION_WRITE_EXTERNAL_STORAGE);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission
                    .READ_EXTERNAL_STORAGE}, RC_PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        requestReadExternalStoragePermission();
        requestWriteExternalStoragePermission();

        FileController fileController = new FileController(this);
        ArrayList<String> galleriesList = fileController.getGalleriesList();
        if (galleriesList.size() >= 1) {
            // Select the first directory.
            fileController.setDirectory(galleriesList.get(0));
        }

        Log.d(TAG, "The next file is: " + fileController.getFile(UiConstants.NEXT));

        uiController = new UiController(this, fileController);
        uiController.createController();

        // Let's try out the database code
        DbTester s = new DbTester(this);
        s.execute();

        decryptTextTest();
decryptFileTest();
    }

    /**
     * Simple method to test encryption and decryption and show a result to the user.
     * @return
     */
    boolean decryptTextTest() {
        // Let's experiment with a given key.
        String keyHardcode="SOh7N8bl1R5ZoJrGLzhzjA==";

        // And let's try out encrypting and decrypting
        try {
            SecretKey skey = new SecretKeySpec(FileController.STob(keyHardcode), "AES");

            // We could generate it like this.
            // SecretKey skey = KeyGenerator.getInstance("AES").generateKey();
            String key = FileController.bToS(skey.getEncoded());
            Log.d(TAG, "I generated this crazy long key: " + key);
            String expected = "This is a long message";
            byte[] testMessage = expected.getBytes(Charsets.UTF_8);
            Pair<byte[],byte[]> m = FileController.encrypt(testMessage, skey);

            byte[] cipherText = m.first;
            byte[] iv = m.second;
            // Let's print that out, and see what we can do with it.
            Log.d(TAG, "I got cipherText " + FileController.bToS(cipherText));

            // And decrypt
            byte[] plainText = FileController.decrypt(cipherText, iv, skey);
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
     * @return
     */
    boolean decryptStreamTest() {
        // Let's experiment with a given key.
        String keyHardcode="SOh7N8bl1R5ZoJrGLzhzjA==";

        // And let's try out encrypting and decrypting
        try {
            SecretKey skey = new SecretKeySpec(FileController.STob(keyHardcode), "AES");

            // We could generate it like this.
            // SecretKey skey = KeyGenerator.getInstance("AES").generateKey();
            String key = FileController.bToS(skey.getEncoded());
            Log.d(TAG, "I generated this crazy long key: " + key);
            String expected = "This is a long message";
            byte[] testMessage = expected.getBytes(Charsets.UTF_8);
            Pair<byte[],byte[]> m = FileController.encrypt(testMessage, skey);

            byte[] cipherText = m.first;
            byte[] iv = m.second;
            // Let's print that out, and see what we can do with it.
            Log.d(TAG, "I got cipherText " + FileController.bToS(cipherText));

            // And decrypt
            byte[] plainText = FileController.decrypt(cipherText, iv, skey);
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
     * @return
     */
    boolean decryptFileTest() {
        // Let's experiment with a given key.
        String keyHardcode="SOh7N8bl1R5ZoJrGLzhzjA==";


        // And let's try out encrypting and decrypting
        try {
            SecretKey skey = new SecretKeySpec(FileController.STob(keyHardcode), "AES");

            // We could generate it like this.
            // SecretKey skey = KeyGenerator.getInstance("AES").generateKey();
            String key = FileController.bToS(skey.getEncoded());
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

            byte[] iv = FileController.encrypt(plainPath, skey, cipherPath);
            if (iv == null) {
                Log.d(TAG, "Encryption failed");
                return false;
            }
            Log.d(TAG, "Encryption succeeded. IV = " + FileController.bToS(iv));

            // Try to decrypt.
            String testPlainPath = Environment.getExternalStorageDirectory().getPath().concat("/")
                    .concat("test.txt");
            boolean result = FileController.decrypt(cipherPath, iv, skey, testPlainPath);
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

    static class DbTester extends AsyncTask<Void, Void, Void> {
        private final Context context;

        DbTester(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            AlbumDatabase db = AlbumDatabase.getDatabase(context);
            db.clearAllTables();
            AlbumDao d = db.albumDao();
            Album s = new Album();
//            s.setId(1);
//            s.setName("Pismo");
//            s.setRemoteLocation("http://nothing.com");
//            List<Album> p = d.getAll();
//            s = p.get(0);
//            Log.d(TAG, "Test album from previous insert = " + s);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        uiController.onWindowFocusChanged(hasFocus);
    }

}
