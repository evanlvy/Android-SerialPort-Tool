package com.licheedev.serialtool.comn;

import android.os.HandlerThread;
import android.serialport.SerialPort;
import com.licheedev.hwutils.ByteUtil;
import com.licheedev.myutils.LogPlus;
import com.licheedev.serialtool.activity.MainActivity;
import com.licheedev.serialtool.comn.message.LogManager;
import com.licheedev.serialtool.comn.message.SendMessage;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Administrator on 2017/3/28 0028.
 */
public class SerialPortManager {

    private static final String TAG = "SerialPortManager";

    private SerialReadThread mReadThread;
    private OutputStream mOutputStream;
    private HandlerThread mWriteThread;
    private Scheduler mSendScheduler;
    private volatile boolean mReadLock = false;

    private static class InstanceHolder {

        public static SerialPortManager sManager = new SerialPortManager();
    }

    public static SerialPortManager instance() {
        return InstanceHolder.sManager;
    }

    private SerialPort mSerialPort;

    private SerialPortManager() {
    }

    /**
     * 打开串口
     *
     * @param device
     * @return
     */
    public SerialPort open(Device device) {
        return open(device.getPath(), device.getBaudrate());
    }

    public boolean open(String path) {
        open(path, "115200");
        return true;
    }

    /**
     * 打开串口
     *
     * @param devicePath
     * @param baudrateString
     * @return
     */
    public SerialPort open(String devicePath, String baudrateString) {
        if (mSerialPort != null) {
            close();
        }

        try {
            File device = new File(devicePath);
            InputStream is = null;
            if (MainActivity.DebugMode) {
                is = new FileInputStream(device);
            } else {
                is = mSerialPort.getInputStream();
                mOutputStream = mSerialPort.getOutputStream();
                int baudRate = Integer.parseInt(baudrateString);
                mSerialPort = new SerialPort(device, baudRate);
            }
            mReadThread = new SerialReadThread(is, data -> mReadLock = false);
            mReadThread.start();

            mWriteThread = new HandlerThread("write-thread");
            mWriteThread.start();
            mSendScheduler = AndroidSchedulers.from(mWriteThread.getLooper());

            return mSerialPort;
        } catch (Throwable tr) {
            LogPlus.e(TAG, "打开串口失败", tr);
            close();
            return null;
        }
    }

    /**
     * 关闭串口
     */
    public void close() {
        if (mReadThread != null) {
            mReadThread.close();
        }
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mWriteThread != null) {
            mWriteThread.quit();
        }

        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
    }

    /**
     * 发送数据
     *
     * @param datas
     * @return
     */
    private void sendData(byte[] datas) throws Exception {
        if (mOutputStream != null) {
            mOutputStream.write(datas);
        }
    }

    /**
     * (rx包裹)发送数据
     *
     * @param datas
     * @return
     */
    private Observable<Object> rxSendData(final byte[] datas) {

        return Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                try {
                    sendData(datas);
                    emitter.onNext(new Object());
                } catch (Exception e) {

                    LogPlus.e("发送：" + (MainActivity.mHexMode?ByteUtil.bytes2HexStr(datas):new String(datas, StandardCharsets.US_ASCII)) + " 失败", e);

                    if (!emitter.isDisposed()) {
                        emitter.onError(e);
                        return;
                    }
                }
                emitter.onComplete();
            }
        });
    }

    /**
     * 发送命令包
     */
    public void sendCommand(final String command, boolean isHex) {

        // TODO: 2018/3/22  
        //LogPlus.i("发送命令：" + command);

        byte[] bytes = new byte[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            bytes = isHex? ByteUtil.hexStr2bytes(command):(command+'\r').getBytes(StandardCharsets.US_ASCII);
        } else {
            bytes = isHex? ByteUtil.hexStr2bytes(command):(command+'\r').getBytes();
        }
        rxSendData(bytes).subscribeOn(mSendScheduler).subscribe(new Observer<Object>() {
            @Override
            public void onSubscribe(Disposable d) {
                
            }

            @Override
            public void onNext(Object o) {
                LogManager.instance().post(new SendMessage(command));
            }

            @Override
            public void onError(Throwable e) {
                LogPlus.e("发送失败", e);
            }

            @Override
            public void onComplete() {

            }
        });
        if (MainActivity.DebugMode) {
            mReadThread.triggerDbgRsp();
        }
    }

    public synchronized void sendCommandSync(String command, boolean isHex, boolean isSync) {
        sendCommand(command, isHex);
        if (!isSync) return;
        int timeout = 1000;
        mReadLock = true;
        try {
            while(timeout > 0 && mReadLock) {
                timeout--;
                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

    }
}
