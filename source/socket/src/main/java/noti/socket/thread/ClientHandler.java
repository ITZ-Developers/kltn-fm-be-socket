package noti.socket.thread;

import io.netty.channel.ChannelHandlerContext;
import noti.common.json.Message;
import noti.socket.cmd.Command;
import noti.socket.cmd.ResponseCode;
import noti.socket.constant.NotiConstant;
import noti.socket.constant.RedisConstant;
import noti.socket.handler.MyChannelWSGroup;
import noti.socket.jwt.UserSession;
import noti.socket.model.ClientChannel;
import noti.socket.model.response.ClientInfoResponse;
import noti.socket.redis.RedisService;
import noti.socket.utils.SocketService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientHandler {
    private static final Logger LOG = LogManager.getLogger(ClientHandler.class);
    private static ClientHandler instance = null;

    private ClientHandler(){

    }

    public static ClientHandler getInstance(){
        if(instance == null){
            instance = new ClientHandler();
        }
        return instance;
    }

    private void sendErrorMsg(ChannelHandlerContext channelHandlerContext, Message oldRequest, String msg){
        Message response = new Message();
        response.setCmd(oldRequest.getCmd());
        response.setMsg(msg);
        response.setResponseCode(ResponseCode.RESPONSE_CODE_ERROR);
        MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(),response.toJson());
    }

    private boolean isValidSession(UserSession userSession) {
        if (userSession == null) {
            return false;
        }
        String tenantName = userSession.getTenantName();
        String posId = userSession.getPosId();
        String sessionId = userSession.getSessionId();
        String grantType = userSession.getGrantType();
        if (StringUtils.isBlank(tenantName) || StringUtils.isBlank(posId) || StringUtils.isBlank(sessionId)) {
            return false;
        }
        String key = "";
        if (NotiConstant.GRANT_TYPE_EMPLOYEE.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_EMPLOYEE, tenantName, posId);
        } else if (NotiConstant.GRANT_TYPE_POS.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_POS, tenantName, posId);
        } else if (NotiConstant.GRANT_TYPE_VIEW.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_VIEW, tenantName, posId);
        }
        if (StringUtils.isNotBlank(key)) {
            return RedisService.getInstance().isValidSession(key, sessionId);
        }
        return false;
    }

    public void handlePing(ChannelHandlerContext channelHandlerContext, Message message) {
        UserSession userSession = UserSession.fromToken(message.getToken());
        if (isValidSession(userSession)) {
            hanldeCacheClientSession(userSession, channelHandlerContext);
            message.setData(new ClientInfoResponse());
            message.setToken(null);
            message.setMsg("Ping success with POS ID: " + userSession.getPosId());
            message.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
            MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
            LOG.info("[Client Ping] Ping success with POS ID: " + userSession.getPosId());
        } else {
            LOG.info("[Client Ping] Token invalid");
            message.setCmd(Command.CMD_LOCK_DEVICE);
            message.setMsg("Token invalid");
            message.setToken(null);
            message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
            MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
        }
    }

    public void handleActivePing(ChannelHandlerContext channelHandlerContext, Message message) {
        String clientChannelId = "ACTIVE_CHANNEL";
        message.setMsg("ACTIVE PING SUCCESS");
        message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
        ClientChannel channel = SocketService.getInstance().getClientChannel(clientChannelId);
        if (channel != null) {
            channel.setTime(System.currentTimeMillis());
            channel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
        } else {
            ClientChannel clientChannel = new ClientChannel();
            clientChannel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            clientChannel.setTime(System.currentTimeMillis());
            SocketService.getInstance().addClientChannel(clientChannelId, clientChannel);
        }
        MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
    }

    public void handleVerifyToken(ChannelHandlerContext channelHandlerContext, Message message) {
        UserSession userSession = UserSession.fromToken(message.getToken());
        if (isValidSession(userSession)) {
            hanldeCacheClientSession(userSession, channelHandlerContext);
            message.setData(new ClientInfoResponse());
            message.setToken(null);
            message.setMsg("Verify token success with POS ID: " + userSession.getPosId());
            message.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
            MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
            LOG.info("[Client Verify Token] Verify token success with POS ID: " + userSession.getPosId());
        } else {
            LOG.info("[Client Verify Token] Token invalid");
            message.setToken(null);
            sendErrorMsg(channelHandlerContext, message, "Token invalid");
        }
    }

    private void hanldeCacheClientSession(UserSession userSession, ChannelHandlerContext channelHandlerContext) {
        String clientChannelId = "";
        Integer keyType = null;
        String grantType = userSession.getGrantType();
        String posId = userSession.getPosId();
        String tenantName = userSession.getTenantName();
        Integer deviceType = userSession.getDeviceType();
        if (NotiConstant.GRANT_TYPE_EMPLOYEE.equals(grantType)) {
            keyType = RedisConstant.KEY_EMPLOYEE;
        } else if (NotiConstant.GRANT_TYPE_POS.equals(grantType)) {
            keyType = RedisConstant.KEY_POS;
        } else if (NotiConstant.GRANT_TYPE_VIEW.equals(grantType)) {
            keyType = RedisConstant.KEY_VIEW;
        } else if (userSession.getId() != null && userSession.getKind() != null) {
            clientChannelId = userSession.getId().toString();
        }
        if (keyType != null) {
            String keyString = RedisService.getInstance().getKeyString(keyType, tenantName, posId);
            RedisService.getInstance().handleUpdateTimeLastUsed(keyString);
            if (RedisConstant.KEY_EMPLOYEE.equals(keyType)) {
                clientChannelId = posId + "&" + tenantName;
            } else {
                clientChannelId = posId + "&" + deviceType;
            }
        }
        ClientChannel channel = SocketService.getInstance().getClientChannel(clientChannelId);
        if(channel != null){
            //update old channel
            channel.setTime(System.currentTimeMillis());
            channel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
        }else{
            //them session mới
            ClientChannel clientChannel = new ClientChannel();
            clientChannel.setPosId(posId);
            clientChannel.setTenantName(tenantName);
            clientChannel.setKeyType(keyType);
            clientChannel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            clientChannel.setTime(System.currentTimeMillis());
            SocketService.getInstance().addClientChannel(clientChannelId, clientChannel);
        }
    }
}
