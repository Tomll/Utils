package com.example.drp.toucheventdispatch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ThreeLevelCacheActivity extends AppCompatActivity {
    LruCache<String, Bitmap> lruCache;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //检查运行时权限：读写SD权限
        if (ActivityCompat.checkSelfPermission(ThreeLevelCacheActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ThreeLevelCacheActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 10086);
        }
        //创建LruCache对象
        long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = (int) (maxMemory / 8);
        lruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        imageView = findViewById(R.id.image);
        //通过三级缓存策略加载图片
        loadBitmapToImageView(imageView, "https://www.baidu.com/img/bd_logo1.png?where=super");
    }


    //通过三级缓存逻辑将图片的Bitmap加载到IamgeView
    public void loadBitmapToImageView(ImageView imageView, String url) {
        if (getBitmapFromMemory(url) != null) {//从内存中查找图片缓存
            Log.d("ThreeLevelCacheActivity", "从内存缓存读取");
            imageView.setImageBitmap(getBitmapFromMemory(url));
        } else if (getBitmapFromLocal(url) != null) {//从本地中查找图片缓存
            Log.d("ThreeLevelCacheActivity", "从本地缓存读取");
            imageView.setImageBitmap(getBitmapFromLocal(url));
        } else {//从网络下载图片
            Log.d("ThreeLevelCacheActivity", "从网络下载");
            getBitmapFromNet(imageView, url);
        }
    }


    //**********一级缓存：内存缓存
    public Bitmap getBitmapFromMemory(String key) {
        return lruCache.get(key);
    }

    public void addBitmapToMemory(String key, Bitmap bitmap) {
        if (null != key && null != bitmap && getBitmapFromMemory(key) == null) {
            lruCache.put(key, bitmap);
        }
    }

    //*************二级缓存：本地文件缓存
    public static final String CACHE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/cache123";

    //从SD卡读取文件缓存
    public Bitmap getBitmapFromLocal(String url) {
        String fileName = MD5Encode(url);
        File file = new File(CACHE_PATH, fileName);
        if (file.exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
                return bitmap;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    //向SD卡保存图片
    public void addBitmapToLocal(String url, Bitmap bitmap) {
        String fileName = MD5Encode(url);
        File file = new File(CACHE_PATH, fileName);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {//如果文件夹不存在，创建文件夹
            parentFile.mkdirs();
        }
        //将图片保存在本地
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    //**************三级缓存：网络下载
    public void getBitmapFromNet(ImageView imageView, String url) {
        new DownImageAsyncTask().execute(imageView, url);//开启异步下载
    }

    private class DownImageAsyncTask extends AsyncTask<Object, Void, Bitmap> {
        private ImageView imageView;
        private String url;

        @Override
        protected Bitmap doInBackground(Object... objects) {//可变参数，数组
            imageView = (ImageView) objects[0];
            url = (String) objects[1];
            Bitmap bitmap = downloadBitmap(url);
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            imageView.setImageBitmap(bitmap);
            //将下载好的图片缓存起来
            if (null != bitmap) {
                addBitmapToMemory(url, bitmap);
                addBitmapToLocal(url, bitmap);
            }
        }
    }

    //同步下载图片方法，必须放在子线程
    @Nullable
    private Bitmap downloadBitmap(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                InputStream in = conn.getInputStream();
                //图片压缩处理
                BitmapFactory.Options option = new BitmapFactory.Options();
                option.inSampleSize = 2;//宽高压缩为原来的1/2，
                option.inPreferredConfig = Bitmap.Config.RGB_565;//设置图片的格式
                Bitmap bitmap = BitmapFactory.decodeStream(in, null, option);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            conn.disconnect();
        }
        return null;
    }


    /**
     * MD5加密
     */
    @NonNull
    public static String MD5Encode(String string) {
        byte[] hash = new byte[0];
        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) {
                hex.append("0");
            }
            hex.append(Integer.toHexString(b & 0xFF));
        }
        return hex.toString();
    }

}
