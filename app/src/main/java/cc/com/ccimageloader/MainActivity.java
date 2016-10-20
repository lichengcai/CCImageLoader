package cc.com.ccimageloader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.imageloaderlib.libcore.io.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String url_img = "http://img.my.csdn.NET/uploads/201309/01/1378037235_7476.jpg";
    DiskLruCache diskLruCache = null;
    private ImageView imageView;

    public void getCache(View view) {
        try {
            DiskLruCache.Snapshot snapshot = diskLruCache.get(hashKeyForDisk(url_img));
            if (snapshot != null) {
                InputStream is = snapshot.getInputStream(0);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                Log.d("getCache"," bitmap---" + bitmap.getByteCount());
                imageView.setImageBitmap(bitmap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void remove(View view) {
        try {
            if (diskLruCache != null) {
                diskLruCache.remove(hashKeyForDisk(url_img));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getS(int code) {
        if (code == 0) {
            Log.d("getS","code---" + code);
            return "code--" + code;
        }

        Log.d("getS","log d d d ");
        return null;
    }

    private void anomic() {
        AtomicInteger ai=new AtomicInteger(0);
        int i1=ai.get();
        v(i1);
        int i2=ai.getAndSet(5);
        v(i2);
        int i3=ai.get();
        v(i3);
        int i4=ai.getAndIncrement();
        v(i4);
        v(ai.get());
    }

    private void v(int i) {
    }

    public void intentTo(View view) {
        startActivity(new Intent(MainActivity.this,SecondActivity.class));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.image);
        int version = getAppVersion(getApplicationContext());
        File file = getDiskCacheDir(getApplicationContext(),"bitmap");

        getS(0);
        anomic();
        Log.d("cpu","cpu count--" + Runtime.getRuntime().availableProcessors());


        if (!file.exists()) {
            file.mkdirs();
        }
        try {
            diskLruCache = DiskLruCache.open(file,version,1,50*1024*1024);
        } catch (IOException e) {
            e.printStackTrace();
        }


        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DiskLruCache.Editor editor = diskLruCache.edit(hashKeyForDisk(url_img));
                    if (editor != null) {
                        OutputStream outputStream = editor.newOutputStream(0);
                        if (downloadUrlToStream(url_img,outputStream)) {
                            editor.commit();
                        }else {
                            editor.abort();
                        }
                    }
                    diskLruCache.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public boolean downloadUrlToStream(String urlImg, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            URL url = new URL(urlImg);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),8*1024);
            out = new BufferedOutputStream(outputStream,8*1024);
            int b;
            while ((b = in.read()) != -1){
                out.write(b);
            }
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }

            try {
                if (out != null) {
                    out.close();
                }
                if (in != null)
                    in.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public File getDiskCacheDir(Context context,String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        }else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath+File.separator+uniqueName);
    }

    public int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            Log.d("getAppVersion","pageName---" + info.packageName + "  version---" + info.versionCode
            + "  versionCode--" + info.versionCode + "  versionName--" + info.versionName
            + "  applicationInfo--" + info.applicationInfo );

            Log.d("getAppVersion"," packageInfo toString---"+ info.toString());
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return 1;
    }
}
