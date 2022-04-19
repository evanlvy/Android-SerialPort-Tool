package com.licheedev.serialtool.comn;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.Toast;

import com.licheedev.hwutils.ByteUtil;
import com.licheedev.myutils.LogPlus;
import com.licheedev.serialtool.activity.MainActivity;
import com.licheedev.serialtool.comn.message.LogManager;
import com.licheedev.serialtool.comn.message.RecvMessage;
import com.licheedev.serialtool.comn.message.SendMessage;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2017/3/28 0028.
 */
public class UsbSerialManager {

    private static final String TAG = "UsbSerialManager";

    public Semaphore mRspLock;
    public volatile int mPortIndex = 0;
    private MainActivity mActivity;
    private String mBaudRate = "230400";
    private ArrayList<String> mPorts = new ArrayList<>();

    private static class InstanceHolder {

        public static UsbSerialManager sManager = new UsbSerialManager();
    }

    public static UsbSerialManager instance() {
        return InstanceHolder.sManager;
    }

    private UsbSerialManager() {
    }

    public interface OnUsbSerialAttachedListener {
        void onAttached(ArrayList<String> ports);
    }
    private OnUsbSerialAttachedListener onUsbSerialAttachedListener;
    public void setOnUsbSerialAttachedListener(OnUsbSerialAttachedListener onUsbSerialAttachedListener) {
        this.onUsbSerialAttachedListener = onUsbSerialAttachedListener;
    }
    /**
     * 打开串口
     *
     * @param path
     * @return
     */
    public boolean open(MainActivity activity, String path) {
        open(activity, path, mBaudRate);
        return true;
    }

    /**
     * 打开串口
     *
     * @param devicePath
     * @param baudrateString
     * @return
     */
    public boolean open(MainActivity activity, String devicePath, String baudrateString) {
        mActivity = activity;
        mBaudRate = baudrateString;
        mHandler = new MyHandler(mActivity);
        mRspLock = new Semaphore(-1);
        onResume();
        return true;
    }

    /**
     * 关闭串口
     */
    public void close() {
        onPause();
    }

    public void setPort(int portIndex) {
        if(portIndex < mPorts.size()) {
            mPortIndex = portIndex;
            LogPlus.i(TAG, "Set USB Port:"+portIndex);
        }
    }

    /**
     * 发送命令包
     */
    public void sendCommand(final String command, boolean isHex) {
        LogPlus.i("Sending cmd: " + command);
        byte[] bytes = new byte[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            bytes = isHex? ByteUtil.hexStr2bytes(command):(command+'\r').getBytes(StandardCharsets.US_ASCII);
        } else {
            bytes = isHex? ByteUtil.hexStr2bytes(command):(command+'\r').getBytes();
        }
        try {
            usbService.write(bytes, mPortIndex);
            LogManager.instance().post(new SendMessage(mPortIndex+"->"+command));
        } catch (Exception e) {
            Toast.makeText(mActivity, e.getMessage(), Toast.LENGTH_SHORT).show();
            LogPlus.e("Sending: " + command + " FAILED!", e);
        }
        if (MainActivity.DebugMode) {
            //mReadThread.triggerDbgRsp();
        }
    }

    public synchronized void sendCommandSync(String command, boolean isHex, boolean isSync) {
        sendCommand(command, isHex);
        if (!isSync) return;
        try {
            boolean isRspOk = mRspLock.tryAcquire(5, TimeUnit.SECONDS);
            if (!isRspOk) {
                // Timeout!
                LogManager.instance().post(new RecvMessage("TIMEOUT!"));
                //mRspLock.release();
                LogPlus.e("Command Timeout!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = null;
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    msg = "USB Ready";
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    msg = "USB Permission not granted";
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    msg = "No USB connected";
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    if (onUsbSerialAttachedListener != null) {
                        onUsbSerialAttachedListener.onAttached(new ArrayList<String>());
                    }
                    msg = "USB disconnected";
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    msg = "USB device not supported";
                    break;
            }
            if (msg != null) {
                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
            }
        }
    };
    private UsbService usbService;
    private MyHandler mHandler;
    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.SYNC_READ:
                    String buffer = (String) msg.obj;
                    int port = msg.arg1;
                    if (UsbSerialManager.instance().mPortIndex == port) {
                        UsbSerialManager.instance().mRspLock.release();
                        LogManager.instance().post(new RecvMessage(buffer));
                        LogPlus.i("Response: " + buffer);
                    }
                    break;
            }
        }
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        mActivity.registerReceiver(mUsbReceiver, filter);
    }

    public void onResume() {
        setFilters();  // Start listening notifications from UsbService
        LogPlus.d("startService");
        Bundle bundle = new Bundle();
        bundle.putString("BAUDRATE", mBaudRate);
        startService(UsbService.class, usbConnection, bundle); // Start UsbService(if it was not started before) and Bind it
    }

    public void onPause() {
        mActivity.unregisterReceiver(mUsbReceiver);
        LogPlus.d("unbindService");
        mActivity.unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(mActivity, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            mActivity.startService(startService);
        }
        Intent bindingIntent = new Intent(mActivity, service);
        LogPlus.d("bindService");
        mActivity.bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            LogPlus.d("onServiceConnected");
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
            usbService.setOnUsbSerialEventListener(new UsbService.OnUsbSerialEventListener() {
                @Override
                public void onAttached(ArrayList<String> ports) {
                    if (ports == null) {
                        mPorts = new ArrayList<String>();
                    } else {
                        mPorts = ports;
                    }
                    if (onUsbSerialAttachedListener != null) {
                        onUsbSerialAttachedListener.onAttached(mPorts);
                    }
                }

                @Override
                public void onError(int errno, String msg) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

}
