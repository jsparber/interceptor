package xyz.hexene.localvpn;

import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import java.util.logging.Logger;

/**
 * Created by jSparber on 10/23/16.
 */

public class LoggerOutput {
    private static Handler output = null;
    private static String outputLog = "";

    public LoggerOutput(Handler mHander) {
        output = mHander;
        if (output != null) {
            Message msg = Message.obtain();
            msg.obj = outputLog;
            output.sendMessage(msg);
        }
    }

    public static int println(String stringToSend) {
        if (output != null) {
            Message msg = Message.obtain();
            msg.obj = stringToSend + "\n";
            outputLog += msg.obj;
            output.sendMessage(msg);
        }
        return 0;
    }


    public static void clear() {
        outputLog = "";
    }
}
