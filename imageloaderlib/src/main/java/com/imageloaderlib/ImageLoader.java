package com.imageloaderlib;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.imageloaderlib.libcore.io.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lichengcai on 2016/10/19.
 */

public class ImageLoader {
    private static final String TAG = "ImageLoader";
    public static final int MESSAGE_POST_RESULT = 1;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_COUNT = CPU_COUNT+1;
    private static final int MAXNUM_POOL_COUNT = CPU_COUNT*2+1;
    private static final long KEEP_TIME = 10L;

    private static final int TAG_KEY_RUI = R.id.image;
    private static final long DISK_CACHE_SIZE = 50*1024*1024;
    private static final int IO_BUFFER_SIZE = 8*1024;
    private static final int DISK_CACHE_INDEX = 0;
    private boolean mIsDiskLruCacheCreated = false;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r,"ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor  THREAD_POOL_EXECUTE = new ThreadPoolExecutor(CORE_POOL_COUNT,
            MAXNUM_POOL_COUNT,KEEP_TIME, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>(),sThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    LoaderResult result = (LoaderResult) msg.obj;
                    ImageView imageView = result.imageView;
                    imageView.setImageBitmap(result.bitmap);
                    String uri = (String) imageView.getTag(TAG_KEY_RUI);
//                    if (uri.equals(result.uri)) {
                        imageView.setImageBitmap(result.bitmap);
//                    }else {
//                        Log.w(TAG,"set image bitmap, but url has changed, ignored!");
//                    }
                    break;
            }

        }
    };
    private LruCache<String,Bitmap> mMemoryCache;
    private DiskLruCache mDisLruCache;
    private Context mContext;

    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory()/1024);
        int cacheSize = maxMemory/8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount()/1024;
            }
        };

        File diskCacheDir = getDiskCacheDir(mContext,"bitmap");

        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }

        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDisLruCache = DiskLruCache.open(diskCacheDir,getAppVersion(mContext),1,DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }


    private void addBitmapToMemory(String key,Bitmap bitmap) {
        if (getBitmapFromMemory(key) == null) {
            mMemoryCache.put(key,bitmap);
        }
    }

    private Bitmap getBitmapFromMemory(String key) {
        return mMemoryCache.get(key);
    }

    private Bitmap loadBitmapFromMemCache(String url) {
        final String key = hashKeyForDisk(url);
        Bitmap bitmap = getBitmapFromMemory(key);
        return bitmap;
    }

    private Bitmap loadBitmapFromDiskCache(String url,int reqWidth,int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG,"load bitmap from UI Thread, it is not recommended!");
        }

        if (mDisLruCache == null)
            return null;

        Bitmap bitmap = null;
        String key = hashKeyForDisk(url);
        DiskLruCache.Snapshot snapshot = mDisLruCache.get(key);
        if (snapshot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            bitmap = ImageResize.decodeSampleBitmapFromStream(fileInputStream,reqWidth,reqHeight);
//            FileDescriptor fileDescriptor = fileInputStream.getFD();
//            bitmap = ImageResize.decodeSampleBitmapFromFileDescriptor(fileDescriptor,reqWidth,reqHeight);
            if (bitmap != null) {
                addBitmapToMemory(key,bitmap);
            }
        }

        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url,int reqWidth,int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit network from UI Thread");
        }

        if (mDisLruCache == null) {
            return null;
        }

        String key = hashKeyForDisk(url);
        DiskLruCache.Editor editor = mDisLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(url,outputStream)) {
                editor.commit();
            }else {
                editor.abort();
            }

            mDisLruCache.flush();
        }

        return loadBitmapFromDiskCache(url,reqWidth,reqHeight);
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

    private Bitmap downLoadBitmapFromUrl(String urlImage) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlImage);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }
    public void bindBitmap(final String uri,final ImageView imageView) {
        bindBitmap(uri,imageView,0,0);
    }

    private void bindBitmap(final String uri, final ImageView imageView, final int reqWidth, final int reqHeight){
        imageView.setTag(TAG_KEY_RUI,uri);
        final Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        final Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap1 = loadBitmap(uri,reqWidth,reqHeight);
                if (bitmap1 != null) {
                    LoaderResult result = new LoaderResult(imageView,uri,bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT,result).sendToTarget();
                }
            }
        };

        THREAD_POOL_EXECUTE.execute(loadBitmapTask);
    }

    public Bitmap loadBitmap(String uri,int reqWidth,int reqHeight) {
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            Log.d(TAG,"loadBitmapFromMemoryCache, uri---" + uri);
            return bitmap;
        }
        try {
            bitmap = loadBitmapFromDiskCache(uri,reqWidth,reqHeight);
            if (bitmap != null) {
                Log.d(TAG,"loadBitmapFromDisk,uri---" + uri);
                return bitmap;
            }
            bitmap = loadBitmapFromHttp(uri,reqWidth,reqHeight);
            Log.d(TAG,"loadBitmapFromHttp,uri---" + uri);


        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitmap != null && !mIsDiskLruCacheCreated) {
            Log.w(TAG,"encounter error, DiskLruCache is not created");
            bitmap = downLoadBitmapFromUrl(uri);
        }

        return bitmap;
    }

    private String hashKeyForDisk(String key) {
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

    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }

        final StatFs statFs = new StatFs(path.getPath());
        return statFs.getAvailableBlocksLong()*statFs.getBlockSizeLong();
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        }else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath+File.separator+uniqueName);
    }

    private int getAppVersion(Context context) {
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

    private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView,String uri,Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }
}
