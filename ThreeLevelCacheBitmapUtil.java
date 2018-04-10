package com.example.drp.toucheventdispatch;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Created by ruipan.dong on 2018/4/10.
 * 三级缓存工具类
 * LruCache-->FileSystem-->Network
 */

public class ThreeLevelCacheBitmapUtil {
    static LruCache<String, Bitmap> lruCache;//强引用集合，内部维护了一个LinkedHashMap

    static {
        //本进程jvm能够从操作系统中挖到的最大内存
        long maxMemory = Runtime.getRuntime().maxMemory();
        //设置缓存值，若LruCache中的缓存值超过了cacheSize，则会将最老、最远使用的缓存内容移除出去
        int cacheSize = (int) (maxMemory / 8);
        //创建LruCache对象
        lruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    //此方法通过三级缓存逻辑将图片的Bitmap加载到IamgeView上
    public static void display(ImageView imageView, String url) {
        Bitmap bitmap;
        bitmap = getBitmapFromMemory(url);
        if (null != bitmap) {//从内存中查找图片缓存
            Log.d("ThreeLevelCacheActivity", "从内存缓存读取");
            imageView.setImageBitmap(bitmap);
            return;
        }
        bitmap = getBitmapFromLocal(url);
        if (null != bitmap) {//从本地中查找图片缓存
            Log.d("ThreeLevelCacheActivity", "从本地缓存读取");
            imageView.setImageBitmap(bitmap);
            return;
        }
        //从网络下载图片
        Log.d("ThreeLevelCacheActivity", "从网络下载");
        getBitmapFromNet(imageView, url);

    }

//////////////////////////////////*******以下为三级缓存逻辑********///////////////////////////////

    //**********一级缓存：内存缓存
    //从内存读取缓存
    public static Bitmap getBitmapFromMemory(String key) {
        return lruCache.get(key);
    }

    //添加缓存到内存
    public static void addBitmapToMemory(String key, Bitmap bitmap) {
        if (null != key && null != bitmap && getBitmapFromMemory(key) == null) {
            lruCache.put(key, bitmap);
        }
    }

    //*************二级缓存：本地文件缓存
    public static final String CACHE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/cache123";

    //从SD卡读取缓存
    @Nullable
    public static Bitmap getBitmapFromLocal(String url) {
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

    //添加缓存到SD卡
    public static void addBitmapToLocal(String url, Bitmap bitmap) {
        String fileName = MD5Encode(url);
        File file = new File(CACHE_PATH, fileName);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {//如果文件夹不存在，创建文件夹
            parentFile.mkdirs();
        }
        try {
            //将图片保存在本地
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    //**************三级缓存：网络下载
    public static void getBitmapFromNet(ImageView imageView, String url) {
        new DownImageAsyncTask().execute(imageView, url);//开启异步下载
    }

    //网络下载异步任务
    private static class DownImageAsyncTask extends AsyncTask<Object, Void, Bitmap> {
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

    //同步下载方法（必须放在子线程）
    @Nullable
    private static Bitmap downloadBitmap(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.connect();
            int responseCode = conn.getResponseCode();
            Log.d("ThreeLevelCacheBitmapUt", "responseCode:" + responseCode);
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
     * MD5加密，主要用于缓存文件名加密
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
