package noti.socket.thread;

import io.netty.channel.ChannelHandlerContext;
import noti.common.json.Message;
import noti.socket.cache.CacheSingleton;
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

import java.util.List;

public class ClientHandler {
    private static final Logger LOG = LogManager.getLogger(ClientHandler.class);
    private static ClientHandler instance = null;

    private ClientHandler() {

    }

    public static ClientHandler getInstance() {
        if (instance == null) {
            instance = new ClientHandler();
        }
        return instance;
    }

    private void sendErrorMsg(ChannelHandlerContext channelHandlerContext, Message oldRequest, String msg) {
        Message response = new Message();
        response.setCmd(oldRequest.getCmd());
        response.setMsg(msg);
        response.setResponseCode(ResponseCode.RESPONSE_CODE_ERROR);
        MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), response.toJson());
    }

    private boolean isValidSession(UserSession userSession) {
        if (userSession == null) {
            return false;
        }
        String username = userSession.getUsername();
        String tenantName = userSession.getTenantName();
        String grantType = userSession.getGrantType();
        String sessionId = userSession.getSessionId();
        String key = "";
        if (NotiConstant.GRANT_TYPE_EMPLOYEE.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_EMPLOYEE, username, tenantName);
        } else if (NotiConstant.GRANT_TYPE_CUSTOMER.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_CUSTOMER, username, null);
        } else if (NotiConstant.GRANT_TYPE_PASSWORD.equals(grantType)) {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_ADMIN, username, null);
        } else {
            key = RedisService.getInstance().getKeyString(RedisConstant.KEY_MOBILE, username, tenantName);
        }
        if (StringUtils.isNotBlank(key)) {
            return CacheSingleton.getInstance().checkSession(key, sessionId);
        }
        return false;
    }

    public void handlePing(ChannelHandlerContext channelHandlerContext, Message message) {
        UserSession userSession = UserSession.fromToken(message.getToken());
        if (isValidSession(userSession)) {
            hanldeCacheClientSession(userSession, channelHandlerContext);
            message.setData(new ClientInfoResponse());
            message.setToken(null);
            message.setMsg("Ping success with user: " + userSession.getUsername());
            message.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
            MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
            LOG.info("[Client Ping] Ping success with user: " + userSession.getUsername());
        } else {
            LOG.info("[Client Ping] Token invalid");
            message.setToken(null);
            sendErrorMsg(channelHandlerContext, message, "Token invalid");
        }
    }

    public void handleVerifyToken(ChannelHandlerContext channelHandlerContext, Message message) {
        UserSession userSession = UserSession.fromToken(message.getToken());
        if (isValidSession(userSession)) {
            hanldeCacheClientSession(userSession, channelHandlerContext);
            message.setData(new ClientInfoResponse());
            message.setToken(null);
            message.setMsg("Verify token success with user: " + userSession.getUsername());
            message.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            message.setResponseCode(ResponseCode.RESPONSE_CODE_SUCCESS);
            MyChannelWSGroup.getInstance().sendMessage(channelHandlerContext.channel(), message.toJson());
            LOG.info("[Client Verify Token] Verify token success with user: " + userSession.getUsername());
        } else {
            LOG.info("[Client Verify Token] Token invalid");
            message.setToken(null);
            sendErrorMsg(channelHandlerContext, message, "Token invalid");
        }
    }

    private void hanldeCacheClientSession(UserSession userSession, ChannelHandlerContext channelHandlerContext) {
        String clientChannelId;
        Integer keyType;
        String grantType = userSession.getGrantType();
        Integer userKind = userSession.getKind();
        String username = userSession.getUsername();
        String tenantName = userSession.getTenantName();
        if (NotiConstant.GRANT_TYPE_EMPLOYEE.equals(grantType)) {
            keyType = RedisConstant.KEY_EMPLOYEE;
        } else if (NotiConstant.GRANT_TYPE_CUSTOMER.equals(grantType)) {
            keyType = RedisConstant.KEY_CUSTOMER;
        } else if (NotiConstant.GRANT_TYPE_PASSWORD.equals(grantType)) {
            keyType = RedisConstant.KEY_ADMIN;
        } else {
            keyType = RedisConstant.KEY_MOBILE;
        }
        if (List.of(RedisConstant.KEY_EMPLOYEE, RedisConstant.KEY_MOBILE).contains(keyType)) {
            clientChannelId = keyType + "&" + username + "&" + userKind + "&" + tenantName;
        } else {
            clientChannelId = keyType + "&" + username + "&" + userKind;
        }
        ClientChannel channel = SocketService.getInstance().getClientChannel(clientChannelId);
        if (channel != null) {
            // update old channel
            channel.setTime(System.currentTimeMillis());
            channel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
        } else {
            // create new session
            ClientChannel clientChannel = new ClientChannel();
            clientChannel.setKeyType(keyType);
            clientChannel.setChannelId(MyChannelWSGroup.getInstance().getIdChannel(channelHandlerContext.channel()));
            clientChannel.setTime(System.currentTimeMillis());
            SocketService.getInstance().addClientChannel(clientChannelId, clientChannel);
        }
    }
}
