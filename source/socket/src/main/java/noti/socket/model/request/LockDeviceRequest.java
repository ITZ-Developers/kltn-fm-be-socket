package noti.socket.model.request;

import lombok.Data;
import noti.socket.constant.RedisConstant;
import noti.socket.model.ABasicRequest;

@Data
public class LockDeviceRequest extends ABasicRequest {
    private String app;
    private Integer keyType;
    private String username;
    private Integer userKind;
    private String tenantName;

    public String getChannelId() {
        switch (getKeyType()) {
            case RedisConstant.KEY_ADMIN:
            case RedisConstant.KEY_CUSTOMER:
                return keyType + "&" + username + "&" + userKind;
            case RedisConstant.KEY_EMPLOYEE:
            case RedisConstant.KEY_MOBILE:
                return keyType + "&" + username + "&" + userKind + "&" + tenantName;
            default:
                return null;
        }
    }
}
