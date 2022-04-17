package com.licheedev.serialtool.comn;

import android.os.SystemClock;
import com.licheedev.hwutils.ByteUtil;
import com.licheedev.myutils.LogPlus;
import com.licheedev.serialtool.activity.MainActivity;
import com.licheedev.serialtool.comn.message.LogManager;
import com.licheedev.serialtool.comn.message.RecvMessage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;


/**
 * 读串口线程
 */
public class SerialReadThread extends Thread {

    private static final String TAG = "SerialReadThread";

    private BufferedInputStream mInputStream;
    private onReceivedCallback callback;
    private Semaphore mDebugRspLock;

    public SerialReadThread(InputStream is, onReceivedCallback _callback) {
        mInputStream = new BufferedInputStream(is);
        callback = _callback;
        mDebugRspLock = new Semaphore(0);
    }

    public interface onReceivedCallback{
        void onReceive(String data);
    }

    public void triggerDbgRsp() {
        //LogPlus.e("DebugRspLock.acquire release");
        mDebugRspLock.release();
    }

    @Override
    public void run() {
        byte[] received = new byte[1024];
        int size;

        LogPlus.i("Start Reading Thread");

        while (true) {

            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                if (MainActivity.DebugMode) {
                    //LogPlus.d("DebugRspLock.acquire+");
                    mDebugRspLock.acquire();
                    //LogPlus.d("DebugRspLock.acquire-");
                    SystemClock.sleep(200);
                    onDataReceive("OK".getBytes(), 2);
                    continue;
                }
                int available = mInputStream.available();

                if (available > 0) {
                    size = mInputStream.read(received);
                    if (size > 0) {
                        onDataReceive(received, size);
                    }
                } else {
                    // 暂停一点时间，免得一直循环造成CPU占用率过高
                    SystemClock.sleep(1);
                }
            } catch (IOException e) {
                LogPlus.e("Read data failed!", e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Thread.yield();
        }

        LogPlus.e("Reading thread finish!");
    }

    /**
     * 处理获取到的数据
     *
     * @param received
     * @param size
     */
    private void onDataReceive(byte[] received, int size) {
        // TODO: 2018/3/22 解决粘包、分包等
        String out = "";
        if (MainActivity.mHexMode) {
            out = ByteUtil.bytes2HexStr(received, 0, size);
        } else {
            out = new String(received, StandardCharsets.US_ASCII);
        }
        //LogPlus.e("ReadThread: call onReceive");
        callback.onReceive(out);
        LogManager.instance().post(new RecvMessage(out));
    }

    /**
     * 停止读线程
     */
    public void close() {

        try {
            mInputStream.close();
        } catch (IOException e) {
            LogPlus.e("Exception", e);
        } finally {
            super.interrupt();
        }
    }
}
