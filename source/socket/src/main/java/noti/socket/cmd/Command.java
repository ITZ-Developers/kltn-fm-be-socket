package noti.socket.cmd;

import java.util.List;

public class Command {
    // Backend command
    public static final String BACKEND_POST_NOTIFICATION = "BACKEND_POST_NOTIFICATION";

    // CLIENT
    public static final String CLIENT_VERIFY_TOKEN = "CLIENT_VERIFY_TOKEN";
    public static final String CLIENT_PING = "CLIENT_PING";
    public static final String CLIENT_LOGIN_QR_CODE = "CLIENT_LOGIN_QR_CODE";

    public static final String CMD_LOCK_DEVICE = "CMD_LOCK_DEVICE";
    public static final String CMD_LOGIN_QR_CODE = "CMD_LOGIN_QR_CODE";
    public static final String CLIENT_RECEIVED_PUSH_NOTIFICATION = "CLIENT_RECEIVED_PUSH_NOTIFICATION";
    public static final String TEST_CMD = "TEST_CMD";


    public static boolean ignoreToken(String cmd) {
        return List.of(
                TEST_CMD,
                CLIENT_LOGIN_QR_CODE
        ).contains(cmd);
    }
}
