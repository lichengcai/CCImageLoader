package com.imageloaderlib;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.imageloaderlib.libcore.io.DiskLruCache;

/**
 * Created by lichengcai on 2016/10/19.
 */

public class ImageLoader {
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
    }


}
