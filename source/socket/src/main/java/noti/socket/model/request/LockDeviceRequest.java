package noti.socket.model.request;

import lombok.Data;
import noti.socket.model.ABasicRequest;

@Data
public class LockDeviceRequest extends ABasicRequest {
    private String app;
    private String posId;
    private Integer deviceType;
    private String tenantName;
    private String deviceToken;
    private String oneSignalApp;

    /**
     * Get channel id<br>
     * From Master: posId + "&" + deviceType<br>
     * From Tenant: posId + "&" + tenantName
     */
    public String getChannelId() {
        return getDeviceType() != null ? getPosId() + "&" + getDeviceType() : getPosId() + "&" + getTenantName();
    }
}
