package noti.socket.thread;

import noti.socket.cmd.ResponseCode;
import noti.socket.constant.CacheKeyConstant;
import noti.socket.handler.MyChannelWSGroup;
import noti.socket.model.ClientChannel;
import noti.socket.model.event.NotificationEvent;
import noti.socket.model.push.PushNotiRequest;
import noti.socket.model.request.LockDeviceDto;
import noti.socket.model.request.LockDeviceRequest;
import noti.socket.model.request.SendAccessTokenForm;
import noti.socket.model.request.SendMessageRequest;
import noti.socket.utils.SocketService;
import org.apache.logging.log4j.Logger;
import noti.common.json.Devices;
import noti.common.json.Message;
import noti.socket.cmd.Command;
import noti.thread.AbstractRunable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class QueueThread extends AbstractRunable {
    private static final Logger LOG = org.apache.logging.log4j.LogManager.getLogger(QueueThread.class);
    private String data;

    public QueueThread(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void run() {
        try {
            LOG.debug("BACKEND CALL =====> " + data);
            Message message = Message.fromJson(data, Message.class);
            if (message != null) {

                switch (message.getApp()) {
                    case Devices.BACKEND_APP:
                        handleBackendApp(message);
                        break;
                    default:
                        LOG.info("NO sub command process with: " + message.getSubCmd());
                }
            } else {
                LOG.error("message null or channel id null");
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void handleBackendApp(Message message) {
        switch (message.getCmd()) {
            case Command.BACKEND_POST_NOTIFICATION:
                handlePostNoti(message);
                break;
            case Command.CMD_LOCK_DEVICE:
                handleLockDevice(message);
                break;
            case Command.CMD_LOGIN_QR_CODE:
                handleLoginQrCode(message);
                break;
            case Command.CMD_CHAT_ROOM_CREATED:
            case Command.CMD_CHAT_ROOM_UPDATED:
            case Command.CMD_CHAT_ROOM_DELETED:
            case Command.CMD_NEW_MESSAGE:
            case Command.CMD_MESSAGE_UPDATED:
                handleBroadCastChatService(message);
                break;
            default:
                LOG.info("NO sub command process with: " + message.getSubCmd());
        }
    }

    /**
     * handlePostNoti
     * {
     * "cmd": "BACKEND_POST_NOTIFICATION",
     * "app": "BACKEND_APP",
     * "data": {
     * "kind": 1,
     * "app": "ELMS",
     * "message": "Noi dung msg here",
     * "userId": 1234,
     * "cmd": "BACKEND_POST_NOTIFICATION"
     * }
     * }
     */
    private void handlePostNoti(Message message) {
        NotificationEvent notificationEvent = message.getDataObject(NotificationEvent.class);
        if (notificationEvent != null && notificationEvent.getUserId() != null) {
            ClientChannel clientChannel = SocketService.getInstance().getClientChannel(notificationEvent.getUserId().toString());
            if (clientChannel != null) {
                PushNotiRequest pushNotiRequest = new PushNotiRequest();
                pushNotiRequest.setApp(notificationEvent.getApp());
                pushNotiRequest.setMessage(notificationEvent.getMessage());

                Message messagePost = new Message();
                messagePost.setCmd(Command.CLIENT_RECEIVED_PUSH_NOTIFICATION);
                messagePost.setApp(Devices.BACKEND_SOCKET_APP);
                messagePost.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
                messagePost.setData(pushNotiRequest);

                MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(), message.toJson());
            } else {
                LOG.debug("Not found user: {}", notificationEvent.getUserId());
            }
        }
    }

    private void handleLockDevice(Message message) {
        LockDeviceRequest lockDeviceRequest = message.getDataObject(LockDeviceRequest.class);
        ClientChannel clientChannel = SocketService.getInstance().getClientChannel(lockDeviceRequest.getChannelId());
        Message msg = createMessage(Command.CMD_LOCK_DEVICE, Devices.BACKEND_SOCKET_APP, lockDeviceRequest,
                "Lock account: " + lockDeviceRequest.getUsername(),
                ResponseCode.RESPONSE_CODE_SUCCESS);
        if (clientChannel != null) {
            MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(), new LockDeviceDto(msg, lockDeviceRequest));
        } else {
            LOG.error("[LOCK DEVICE] Cannot send message to channel null");
        }
    }

    private Message createMessage(String cmd, String app, Object data, String msg, int responseCode) {
        Message message = new Message();
        message.setCmd(cmd);
        message.setApp(app);
        message.setData(data);
        message.setMsg(msg);
        message.setResponseCode(responseCode);
        return message;
    }

    private void handleLoginQrCode(Message message) {
        SendAccessTokenForm form = message.getDataObject(SendAccessTokenForm.class);
        ClientChannel clientChannel = SocketService.getInstance().getClientChannel(form.getClientId());
        Message msg = createMessage(Command.CMD_LOGIN_QR_CODE, Devices.BACKEND_SOCKET_APP, form,
                "Verify login qr code success",
                ResponseCode.RESPONSE_CODE_SUCCESS);
        if (clientChannel != null) {
            MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(), msg.toJson());
        } else {
            LOG.error("[LOGIN QR CODE] Cannot send message to channel null");
        }
    }

    private void handleBroadCastChatService(Message message) {
        SendMessageRequest request = message.getDataObject(SendMessageRequest.class);
        List<Long> userIds = request.getMemberIds();
        String tenant = request.getTenantName();

        if (userIds == null || tenant == null) {
            LOG.warn("[CHAT SERVICE] Missing tenant or memberIds");
            return;
        }

        request.setMemberIds(null);
        Message msg = createMessage(message.getCmd(), Devices.BACKEND_SOCKET_APP, request, "Broadcast success", ResponseCode.RESPONSE_CODE_SUCCESS);

        SocketService socketService = SocketService.getInstance();
        ConcurrentHashMap<String, ClientChannel> userChannels = socketService.getUserChannels();

        for (Map.Entry<String, ClientChannel> entry : userChannels.entrySet()) {
            ClientChannel channel = entry.getValue();
            if (channel == null) {
                continue;
            }

            boolean sameTenant = tenant.equals(channel.getTenantName());
            boolean isTargetUser = userIds.contains(channel.getUserId());
            boolean validKeyType = List.of(CacheKeyConstant.KEY_EMPLOYEE, CacheKeyConstant.KEY_MOBILE).contains(channel.getKeyType());

            if (sameTenant && isTargetUser && validKeyType) {
                ClientChannel clientChannel = socketService.getClientChannel(entry.getKey());
                if (clientChannel != null) {
                    MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(), msg.toJson());
                } else {
                    LOG.error("[CHAT SERVICE] Channel ID null for key: {}", entry.getKey());
                }
            }
        }
    }
}
