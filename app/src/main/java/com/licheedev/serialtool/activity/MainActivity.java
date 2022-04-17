package com.licheedev.serialtool.activity;

import android.content.Intent;
import android.os.Bundle;
import android.serialport.SerialPortFinder;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.text.method.TextKeyListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import butterknife.BindView;
import butterknife.OnClick;

import com.licheedev.serialtool.R;
import com.licheedev.serialtool.activity.base.BaseActivity;
import com.licheedev.serialtool.comn.Device;
import com.licheedev.serialtool.comn.SerialPortManager;
import com.licheedev.serialtool.comn.UsbSerialManager;
import com.licheedev.serialtool.util.AllCapTransformationMethod;
import com.licheedev.serialtool.util.Logf;
import com.licheedev.serialtool.util.PrefHelper;
import com.licheedev.serialtool.util.ToastUtil;
import com.licheedev.serialtool.util.constant.PreferenceKeys;

import static com.licheedev.serialtool.R.array.baudrates;


public class MainActivity extends BaseActivity implements AdapterView.OnItemSelectedListener {

    @BindView(R.id.spinner_devices)
    Spinner mSpinnerDevices;
    @BindView(R.id.spinner_baudrate)
    Spinner mSpinnerBaudrate;
    @BindView(R.id.btn_open_device)
    Button mBtnOpenDevice;
    @BindView(R.id.btn_send_data)
    Button mBtnSendData;
    @BindView(R.id.btn_load_list)
    Button mBtnLoadList;
    @BindView(R.id.et_data)
    EditText mEtData;
    @BindView(R.id.sw_hex)
    Switch mSwHexMode;
    @BindView(R.id.sw_usb)
    Switch mSwUsbMode;
    @BindView(R.id.spinner_usb)
    Spinner mSpinnerUsbs;

    private Device mDevice;

    private int mSerialDeviceIndex;
    private int mBaudrateIndex;
    public int mUsbSerialIndex = 0;

    private String[] mDevices;
    private String[] mBaudrates;
    private String[] mUsbPorts;

    private boolean mOpened = false;
    public static boolean mHexMode = false;
    public static boolean mUsbMode = true;

    public static final boolean DebugMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEtData.setTransformationMethod(new AllCapTransformationMethod(true));

        initDevice();
        initUsbSerial();
        initSpinners();
        updateViewState(mOpened);
        mSwHexMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mHexMode = isChecked;
                mEtData.setText("");
                if (isChecked) {
                    mEtData.setKeyListener(DigitsKeyListener.getInstance("0123456789abcdefABCDEF"));
                } else {
                    mEtData.setKeyListener(TextKeyListener.getInstance(false, TextKeyListener.Capitalize.WORDS));
                }
            }
        });
        mSwUsbMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mUsbMode = isChecked;
                updateViewState(mOpened);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mUsbMode) {
            UsbSerialManager.instance().close();
        } else {
            SerialPortManager.instance().close();
        }
        super.onDestroy();
    }

    @Override
    protected boolean hasActionBar() {
        return false;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    private void initUsbSerial() {

    }
    /**
     * 初始化设备列表
     */
    private void initDevice() {

        SerialPortFinder serialPortFinder = new SerialPortFinder();

        // 设备
        mDevices = serialPortFinder.getAllDevicesPath();
        if (mDevices.length == 0) {
            mDevices = new String[] {
                getString(R.string.no_serial_device)
            };
        }

        mUsbPorts = new String[] {getString(R.string.waiting_usb)};

        // 波特率
        mBaudrates = getResources().getStringArray(baudrates);

        mSerialDeviceIndex = PrefHelper.getDefault().getInt(PreferenceKeys.SERIAL_PORT_DEVICES, 0);
        mSerialDeviceIndex = mSerialDeviceIndex >= mDevices.length ? mDevices.length - 1 : mSerialDeviceIndex;
        mBaudrateIndex = PrefHelper.getDefault().getInt(PreferenceKeys.BAUD_RATE, 9);

        mDevice = new Device(mDevices[mSerialDeviceIndex], mBaudrates[mBaudrateIndex]);
    }

    /**
     * 初始化下拉选项
     */
    private void initSpinners() {

        ArrayAdapter<String> deviceAdapter =
            new ArrayAdapter<String>(this, R.layout.spinner_default_item, mDevices);
        deviceAdapter.setDropDownViewResource(R.layout.spinner_item);
        mSpinnerDevices.setAdapter(deviceAdapter);
        mSpinnerDevices.setOnItemSelectedListener(this);

        ArrayAdapter<String> usbAdapter =
            new ArrayAdapter<String>(this, R.layout.spinner_default_item, mUsbPorts);
        usbAdapter.setDropDownViewResource(R.layout.spinner_item);
        mSpinnerUsbs.setAdapter(usbAdapter);
        mSpinnerUsbs.setOnItemSelectedListener(this);

        ArrayAdapter<String> baudrateAdapter =
            new ArrayAdapter<String>(this, R.layout.spinner_default_item, mBaudrates);
        baudrateAdapter.setDropDownViewResource(R.layout.spinner_item);
        mSpinnerBaudrate.setAdapter(baudrateAdapter);
        mSpinnerBaudrate.setOnItemSelectedListener(this);

        mSpinnerDevices.setSelection(mSerialDeviceIndex);
        mSpinnerBaudrate.setSelection(mBaudrateIndex);
    }

    @OnClick({ R.id.btn_open_device, R.id.btn_send_data, R.id.btn_load_list})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_open_device:
                Logf.getInstance().init(MainActivity.this);
                switchSerialPort();
                break;
            case R.id.btn_send_data:
                sendData();
                break;
            case R.id.btn_load_list:
                Intent intent = new Intent(this, LoadCmdListActivity.class);
                intent.putExtra("USBSERIAL", mUsbMode);
                startActivity(intent);
                break;
        }
    }

    private void sendData() {

        String text = mEtData.getText().toString().trim();
        if (TextUtils.isEmpty(text) || (mHexMode && text.length() % 2 != 0)) {
            ToastUtil.showOne(this, R.string.invalid_data);
            return;
        }
        if (mUsbMode) {
            UsbSerialManager.instance().sendCommand(text, mHexMode);
        } else {
            SerialPortManager.instance().sendCommand(text, mHexMode);
        }
    }

    /**
     * 打开或关闭串口
     */
    private void switchSerialPort() {
        if (mOpened) {
            SerialPortManager.instance().close();
            UsbSerialManager.instance().close();
            mOpened = false;
        } else if (DebugMode) {
            String path = getExternalFilesDir(null)+"/test_dev.txt";
            mOpened = SerialPortManager.instance().open(path);
            ToastUtil.showOne(this, R.string.toast_start_debug_mode);
        } else {
            // 保存配置
            PrefHelper.getDefault().saveInt(PreferenceKeys.SERIAL_PORT_DEVICES, mSerialDeviceIndex);
            PrefHelper.getDefault().saveInt(PreferenceKeys.BAUD_RATE, mBaudrateIndex);
            if (mUsbMode) {
                mOpened = UsbSerialManager.instance().open(MainActivity.this, "", mBaudrates[mBaudrateIndex]);
                UsbSerialManager.instance().setOnUsbSerialAttachedListener(new UsbSerialManager.OnUsbSerialAttachedListener() {
                    @Override
                    public void onAttached(String[] ports) {
                        mUsbPorts = ports;
                        ArrayAdapter<String> usbAdapter =
                                new ArrayAdapter<String>(MainActivity.this, R.layout.spinner_default_item, mUsbPorts);
                        mSpinnerUsbs.setAdapter(usbAdapter);
                        int port_idx = -1, selector_idx = 0;
                        for (int i=0; i<mUsbPorts.length; i++) {
                            int port =  Integer.parseInt(""+mUsbPorts[i].charAt(0));
                            if (port > port_idx) {
                                port_idx = port;
                                selector_idx = i;
                            }
                        }
                        mSpinnerUsbs.setSelection(selector_idx);  // Use the second port by default
                        mUsbSerialIndex = port_idx;
                        UsbSerialManager.instance().setPort(mUsbSerialIndex);
                    }
                });
            } else {
                mOpened = SerialPortManager.instance().open(mDevice) != null;
            }
            if (mOpened) {
                ToastUtil.showOne(this, R.string.open_port_ok);
            } else {
                ToastUtil.showOne(this, R.string.open_port_fail);
            }
        }
        updateViewState(mOpened);
    }

    /**
     * 更新视图状态
     *
     * @param isSerialPortOpened
     */
    private void updateViewState(boolean isSerialPortOpened) {

        int stringRes = isSerialPortOpened ? R.string.close_serial_port : R.string.open_serial_port;

        mBtnOpenDevice.setText(stringRes);

        mSpinnerDevices.setEnabled(!isSerialPortOpened);
        mSpinnerBaudrate.setEnabled(!isSerialPortOpened);
        mBtnSendData.setEnabled(isSerialPortOpened);
        mBtnLoadList.setEnabled(isSerialPortOpened);

        mSwUsbMode.setEnabled(!isSerialPortOpened);
        mSwHexMode.setEnabled(!isSerialPortOpened);

        if (mUsbMode) {
            mSpinnerDevices.setVisibility(View.GONE);
            mSpinnerUsbs.setVisibility(View.VISIBLE);
        } else {
            mSpinnerUsbs.setVisibility(View.GONE);
            mSpinnerDevices.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        // Spinner 选择监听
        switch (parent.getId()) {
            case R.id.spinner_devices:
                mSerialDeviceIndex = position;
                mDevice.setPath(mDevices[mSerialDeviceIndex]);
                break;
            case R.id.spinner_baudrate:
                mBaudrateIndex = position;
                mDevice.setBaudrate(mBaudrates[mBaudrateIndex]);
                break;
            case R.id.spinner_usb:
                String selected = mUsbPorts[position];
                try {
                    int port_idx = Integer.parseInt(""+selected.charAt(0));
                    if (port_idx >= 0 && port_idx <= (mUsbPorts.length - 1)) {
                        mUsbSerialIndex = port_idx;
                        UsbSerialManager.instance().setPort(mUsbSerialIndex);
                    }
                } catch (Exception e){}
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // 空实现
    }
}
