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
        imageView = findViewById(R.id.image);
        //通过三级缓存工具类加载图片
        ThreeLevelCacheBitmapUtil.display(imageView,"https://www.baidu.com/img/bd_logo1.png?where=super");
    }



}
