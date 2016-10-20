package cc.com.ccimageloader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

import com.imageloaderlib.ImageLoader;

/**
 * Created by lichengcai on 2016/10/20.
 */

public class SecondActivity extends Activity {
    private ImageView imageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        imageView = (ImageView) findViewById(R.id.image_second);

        String url = "http://img.my.csdn.net/uploads/201407/26/1406382880_3865.jpg";

        ImageLoader.build(getApplicationContext()).bindBitmap(url,imageView);
    }
}
