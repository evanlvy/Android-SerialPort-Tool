package com.licheedev.serialtool.comn.message;

import com.licheedev.serialtool.util.TimeUtil;

/**
 * 发送的日志
 */

public class SendMessage implements IMessage {

    private String command;
    private String message;

    public SendMessage(String command) {
        this.command = command;
        this.message = TimeUtil.currentTime() + "    Command: " + command;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isToSend() {
        return true;
    }
}
