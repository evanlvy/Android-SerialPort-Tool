package com.licheedev.serialtool.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;

import butterknife.BindView;
import butterknife.OnClick;
import com.licheedev.myutils.LogPlus;
import com.licheedev.serialtool.R;
import com.licheedev.serialtool.activity.base.BaseActivity;
import com.licheedev.serialtool.comn.SerialPortManager;
import com.licheedev.serialtool.comn.UsbSerialManager;
import com.licheedev.serialtool.model.Command;
import com.licheedev.serialtool.util.BaseListAdapter;
import com.licheedev.serialtool.util.CommandParser;
import com.licheedev.serialtool.util.ListViewHolder;
import com.licheedev.serialtool.util.ToastUtil;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.bartwell.exfilepicker.ExFilePicker;
import ru.bartwell.exfilepicker.data.ExFilePickerResult;

public class LoadCmdListActivity extends BaseActivity implements AdapterView.OnItemClickListener {

    public static final int REQUEST_FILE = 233;
    @BindView(R.id.btn_load_list)
    Button mBtnLoadList;
    @BindView(R.id.list_view)
    ListView mListView;
    @BindView(R.id.btn_send_list)
    Button mBtnSendList;
    @BindView(R.id.sw_loop)
    Switch mSwLoop;

    private ExFilePicker mFilePicker;
    private CommandParser mParser;
    private InnerAdapter mAdapter;
    private boolean mInLoop = true;  // Keep state with layout property
    private boolean mIsSending = false;
    private boolean mShouldStop = false;
    private boolean mUsbMode = true;
    private Handler mHandler = new Handler();

    public static final String default_cmd_list[] = {
            "ATD//init",
            "ATH//init",
            "ATE0",
            "ATS1",
            "ATU1",
            "ATD13888888888 //Dial test"
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_load_cmd_list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActionBar(true, R.string.load_cmd_list);
        initFilePickers();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mUsbMode = extras.getBoolean("USBSERIAL");
        }
        mParser = new CommandParser();

        mListView.setOnItemClickListener(this);
        mAdapter = new InnerAdapter();
        mListView.setAdapter(mAdapter);

        mSwLoop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mInLoop = isChecked;
            }
        });

        if (default_cmd_list != null) {
            ArrayList<Command> default_commands = new ArrayList<>();
            for (int i = 0; i < default_cmd_list.length; i++) {
                String splitted[] = default_cmd_list[i].split("//", 2);
                String cmd_str, comment_str = null;
                if (splitted.length >= 2) {
                    // with comment ready // comment with init means do not send in the loop
                    comment_str = splitted[1];
                }
                cmd_str = splitted[0];
                Command command = new Command(cmd_str, TextUtils.isEmpty(comment_str) ? getString(R.string.no_name) : comment_str);
                default_commands.add(command);
            }
            mAdapter.setNewData(default_commands);
        }
    }

    @Override
    protected void onDestroy() {
        mShouldStop = true;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE) {
            ExFilePickerResult result = ExFilePickerResult.getFromIntent(data);
            if (result != null && result.getCount() > 0) {
                File file = new File(result.getPath(), result.getNames().get(0));

                mParser.rxParse(file).subscribe(new Observer<List<Command>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(List<Command> commands) {
                        mAdapter.setNewData(commands);
                    }

                    @Override
                    public void onError(Throwable e) {
                        LogPlus.e(getString(R.string.parse_fail), e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
            } else {
                ToastUtil.showOne(this, R.string.file_not_selected);
            }
        }
    }

    /**
     * 初始化文件/文件夹选择器
     */
    private void initFilePickers() {

        mFilePicker = new ExFilePicker();
        mFilePicker.setNewFolderButtonDisabled(true);
        mFilePicker.setQuitButtonEnabled(true);
        mFilePicker.setUseFirstItemAsUpEnabled(true);
        mFilePicker.setCanChooseOnlyOneItem(true);
        mFilePicker.setShowOnlyExtensions("txt");
        mFilePicker.setChoiceType(ExFilePicker.ChoiceType.FILES);
    }

    @OnClick({ R.id.btn_load_list, R.id.btn_send_list })
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_load_list:
                mFilePicker.start(this, REQUEST_FILE);
                break;
            case R.id.btn_send_list:
                if (mIsSending) {
                    mShouldStop = true;
                    break;
                }
                mShouldStop = false;
                new Thread(() -> {
                    if (mInLoop) {
                        startSendListLoop();
                    } else {
                        sendList(0);
                    }
                }).start();
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Command item = mAdapter.getItem(position);
        sendCmd(item, false);
    }

    private void sendCmd(Command item, boolean isSync) {
        String cmd = item.getCommand();
        String regex="^[A-Fa-f0-9]+$";
        boolean isHex = cmd.matches(regex);
        if (mUsbMode) {
            UsbSerialManager.instance().sendCommandSync(cmd, isHex, isSync);
        } else {
            SerialPortManager.instance().sendCommandSync(cmd, isHex, isSync);
        }
    }

    private static class InnerAdapter extends BaseListAdapter<Command> {
        @Override
        protected void inflateItem(ListViewHolder holder, int position) {

            Command item = getItem(position);

            String comment = String.valueOf(position + 1);
            comment =
                TextUtils.isEmpty(item.getComment()) ? comment : comment + " " + item.getComment();

            holder.setText(R.id.tv_comment, comment);
            holder.setText(R.id.tv_command, item.getCommand());
        }

        @Override
        public int getItemLayoutId(int viewType) {
            return R.layout.item_load_command_list;
        }
    }

    private boolean startSendListLoop() {
        sendList(1);
        while (!mShouldStop) {
            sendList(2);
        }
        return true;
    }

    private synchronized boolean sendList(int range){
        // Send command Range of the list: 0---all cmd, 1---init cmd only, 2---non-init cmd only
        int cnt = mAdapter.getCount();
        mIsSending = true;
        mBtnSendList.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        mBtnSendList.setText(R.string.stop_send);
                    }
                }, 100
        );
        for (int i = 0; i< cnt; i++) {
            if (mShouldStop) break;
            Command item = mAdapter.getItem(i);
            String comment = item.getComment();
            boolean isInitCmd = comment.toLowerCase().startsWith("init") ;
            if ((isInitCmd && range >= 2) || (!isInitCmd && range == 1)) {
                continue;
            }
            sendCmd(item, true);
        }
        mIsSending = false;
        mBtnSendList.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        mBtnSendList.setText(R.string.send_by_list);
                    }
                }, 100
        );
        return true;
    }
}
