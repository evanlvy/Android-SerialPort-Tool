package com.licheedev.serialtool.util;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.Locale;

public class Logf {
    private static Logf INSTANCE = new Logf();
    private Logf() {};
    public static Logf getInstance() {
        return(INSTANCE);
    }
    private static String logPath;
    private SimpleDateFormat dateFormat = null;
    public void init(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            logPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/log"+dateFormat.format(new Date())+".txt";
        }
        else {
            dateFormat = null;
            Long tsLong = System.currentTimeMillis()/1000;
            logPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/log"+tsLong+".txt";
        }
    }
    public static final String TAG_JSON = "JSON";
    public static final String TAG_USER = "USER";
    public static final String TAG_RESTRICTION = "RESTRICTION";
    private static final char VERBOSE = 'v';
    private static final char DEBUG = 'd';
    private static final char INFO = 'i';
    private static final char WARN = 'w';
    private static final char ERROR = 'e';
    public static void v(String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) Log.v(tag, msg);
        else getInstance().writeToFile(VERBOSE, tag, msg);
    }
    public static void d(String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) Log.d(tag, msg);
        else getInstance().writeToFile(DEBUG, tag, msg);
    }
    public static void i(String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) Log.i(tag, msg);
        else getInstance().writeToFile(INFO, tag, msg);
    }
    public static void w(String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) Log.w(tag, msg);
        else getInstance().writeToFile(WARN, tag, msg);
    }
    public static void e(String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) Log.e(tag, msg);
        else getInstance().writeToFile(ERROR, tag, msg);
    }
    private void writeToFile(char type, String tag, String msg) {
        if (TextUtils.isEmpty(logPath)) {
            return;
        }
        synchronized (Logf.class) {
            String log = null;
            if (dateFormat != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                log = dateFormat.format(new Date()) + " " + type + " " + tag + " " + msg + "\n";
            } else {
                Long tsLong = System.currentTimeMillis()/1000;
                log = tsLong + ": " + type + " " + tag + " " + msg + "\n";
            }
            BufferedWriter bw = null;
            try {
                File file = new File(logPath);
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                if (!file.exists()) {
                    file.createNewFile();
                }
                FileOutputStream fs = new FileOutputStream(file, true);
                bw = new BufferedWriter(new OutputStreamWriter(fs));
                bw.write(log);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (bw != null) {
                        bw.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
