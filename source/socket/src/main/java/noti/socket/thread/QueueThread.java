package noti.socket.thread;

import noti.socket.cmd.ResponseCode;
import noti.socket.constant.NotiConstant;
import noti.socket.handler.MyChannelWSGroup;
import noti.socket.model.ClientChannel;
import noti.socket.model.event.NotificationEvent;
import noti.socket.model.push.PushNotiRequest;
import noti.socket.model.request.LockDeviceDto;
import noti.socket.model.request.LockDeviceRequest;
import noti.socket.onesignal.OneSignalSingleton;
import noti.socket.onesignal.RequestPushNotification;
import noti.socket.utils.SocketService;
import org.apache.logging.log4j.Logger;
import noti.common.json.Devices;
import noti.common.json.Message;
import noti.socket.cmd.Command;
import noti.thread.AbstractRunable;


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
            LOG.debug("BACKEND CALL =====> "+data);
            Message message = Message.fromJson(data, Message.class);
            if (message != null) {

                switch (message.getApp()) {
                    case Devices.BACKEND_APP:
                        hanldeBackendApp(message);
                        break;
                    default:
                        LOG.info("NO sub command process with: " + message.getSubCmd());
                }
            } else {
                LOG.error("message null or channel id null");
            }
        }catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }

    private void hanldeBackendApp(Message message){
        switch (message.getCmd()) {
            case Command.BACKEND_POST_NOTIFICATION:
                handlePostNoti(message);
                break;
            case Command.CMD_LOCK_DEVICE:
                handleLockDevice(message);
                break;
            default:
                LOG.info("NO sub command process with: " + message.getSubCmd());
        }
    }

    /**
     *
     * handlePostNoti
     *{
     * 	"cmd": "BACKEND_POST_NOTIFICATION",
     * 	"app": "BACKEND_APP",
     * 	"data": {
     * 		"kind": 1,
     * 		"app": "ELMS",
     * 		"message": "Noi dung msg here",
     * 		"userId": 1234,
     * 		"cmd": "BACKEND_POST_NOTIFICATION"
     *   }
     * }
     * */
    private void handlePostNoti(Message message){
        NotificationEvent notificationEvent = message.getDataObject(NotificationEvent.class);
        if(notificationEvent != null && notificationEvent.getUserId()!=null){
            ClientChannel clientChannel = SocketService.getInstance().getClientChannel(notificationEvent.getUserId().toString());
            if(clientChannel != null){
                PushNotiRequest pushNotiRequest = new PushNotiRequest();
                pushNotiRequest.setApp(notificationEvent.getApp());
                pushNotiRequest.setMessage(notificationEvent.getMessage());

                Message messagePost = new Message();
                messagePost.setCmd(Command.CLIENT_RECEIVED_PUSH_NOTIFICATION);
                messagePost.setApp(Devices.BACKEND_SOCKET_APP);
                messagePost.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
                messagePost.setData(pushNotiRequest);

                MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(),message.toJson());
            }else{
                LOG.debug("Not found user: {}", notificationEvent.getUserId());
            }
        }
    }

    private void handleLockDevice(Message message) {
        LockDeviceRequest lockDeviceRequest = message.getDataObject(LockDeviceRequest.class);
        ClientChannel clientChannel = SocketService.getInstance().getClientChannel(lockDeviceRequest.getChannelId());
        Message msg = createMessage(Command.CMD_LOCK_DEVICE, Devices.BACKEND_SOCKET_APP, lockDeviceRequest,
                "Lock device with POS ID of Restaurant - " + lockDeviceRequest.getTenantName() + ": " + lockDeviceRequest.getPosId(),
                ResponseCode.RESPONSE_CODE_SUCCESS);
        boolean isMasterAppValid = NotiConstant.APP_MASTER.equals(lockDeviceRequest.getApp())
                && lockDeviceRequest.getPosId() != null
                && lockDeviceRequest.getDeviceType() != null;
        boolean isTenantAppValid = NotiConstant.APP_TENANT.equals(lockDeviceRequest.getApp())
                && lockDeviceRequest.getPosId() != null
                && lockDeviceRequest.getTenantName() != null;
        if (clientChannel != null && (isMasterAppValid || isTenantAppValid)) {
            MyChannelWSGroup.getInstance().sendMessage(clientChannel.getChannelId(), new LockDeviceDto(msg, lockDeviceRequest));
        } else {
            OneSignalSingleton.getInstance().sendNotification(new RequestPushNotification(
                    msg.getMsg(),
                    msg.toJson(),
                    lockDeviceRequest.getDeviceToken(),
                    lockDeviceRequest.getOneSignalApp()
            ));
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
}
