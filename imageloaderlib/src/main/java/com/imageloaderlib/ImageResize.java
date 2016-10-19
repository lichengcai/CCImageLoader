package com.imageloaderlib;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;

/**
 * Created by lichengcai on 2016/10/19.
 */

public class ImageResize {
    private static final String TAG = "ImageResize";

    public ImageResize() {}

    public static Bitmap decodeSampleBitmapFromResource(Resources resources,int resId,int reqWidth,int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeResource(resources,resId,options);
        if (bitmap == null) {
            Log.d(TAG,"bitmap is null now decodeSampleBitmapFromResource");
        }
        int sampleSize = calculateSampleSize(options,reqWidth,reqHeight);
        options.inSampleSize = sampleSize;
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeResource(resources,resId,options);
    }


    public static Bitmap decodeSampleBitmapFromFileDescriptor(FileDescriptor fd,int reqWidth,int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd,null,options);
        if (bitmap == null) {
            Log.d(TAG,"bitmap is null now decodeSampleBitmapFromFileDescriptor");
        }
        int sampleSize = calculateSampleSize(options,reqWidth,reqHeight);
        options.inSampleSize = sampleSize;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFileDescriptor(fd,null,options);
    }

    private static int calculateSampleSize(BitmapFactory.Options options,int reqWidth,int reqHeight) {
        if (reqHeight == 0 || reqWidth ==0) {
            return 1;
        }

        //Raw width and height of image
        final int width = options.outWidth;
        final int height = options.outHeight;

        int sampleSize = 1;
        if (width > reqHeight || height > reqHeight) {
            final int halfWidth = width/2;
            final int halfHeight = height/2;

            while ((halfHeight/sampleSize)>reqHeight && (halfWidth/sampleSize)>reqWidth) {
                sampleSize *= 2;
            }
        }
        Log.d(TAG,"sampleSize---" + sampleSize);
        return sampleSize;
    }
}
