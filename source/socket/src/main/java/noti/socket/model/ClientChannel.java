package noti.socket.model;

import lombok.Data;

@Data
public class ClientChannel {
    private String channelId;
    private String posId;
    private String tenantName;
    private Integer keyType;
    private Long time;
}
