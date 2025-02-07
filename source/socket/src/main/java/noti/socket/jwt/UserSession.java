package noti.socket.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Data;
import noti.socket.constant.NotiConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import noti.socket.utils.SocketService;

@Data
public class UserSession {
    private static final Logger LOG = org.apache.logging.log4j.LogManager.getLogger(UserSession.class);
    private Long id;
    private Integer kind;
    private String posId;
    private Integer deviceType;
    private String tenantInfo;
    private String tenantName;
    private String sessionId;
    private String grantType;


    public static UserSession fromToken(String token){
        String publicKey = SocketService.getInstance().getStringResource("server.public.key");
        try {
            //System.out.println("==========> JWT secrect key: "+secretKey);
            Algorithm algorithm = Algorithm.HMAC256(publicKey);
            JWTVerifier verifier = JWT.require(algorithm)
                    .acceptLeeway(1) //1 sec for nbf and iat
                    .acceptExpiresAt(5) //5 secs for exp
                    .build();
            DecodedJWT decodedJWT = verifier.verify(token);
            Long userId = decodedJWT.getClaim("user_id").asLong();
            Integer userKind = decodedJWT.getClaim("user_kind").asInt();
            String posId = decodedJWT.getClaim("pos_id").asString();
            Integer deviceType = decodedJWT.getClaim("device_type").asInt();
            String tenantInfo = decodedJWT.getClaim("tenant_info").asString();
            String tenantName = decodedJWT.getClaim("tenant_name").asString();
            String sessionId = decodedJWT.getClaim("session_id").asString();
            String grantType = decodedJWT.getClaim("grant_type").asString();
            if (StringUtils.isBlank(grantType)) {
                return null;
            }
            UserSession userSession = new UserSession();
            userSession.setGrantType(grantType);
            if (StringUtils.isNoneBlank(tenantInfo)) {
                userSession.setTenantInfo(tenantInfo);
            }
            if (StringUtils.isNotBlank(sessionId)) {
                userSession.setSessionId(sessionId);
            }
            if (StringUtils.isNoneBlank(tenantName)) {
                userSession.setTenantName(tenantName);
            }
            if (deviceType != null) {
                userSession.setDeviceType(deviceType);
            }
            if (posId != null) {
                userSession.setPosId(posId);
            }
            if (NotiConstant.GRANT_TYPE_POS.equals(grantType)
                    || NotiConstant.GRANT_TYPE_VIEW.equals(grantType)
                    || NotiConstant.GRANT_TYPE_EMPLOYEE.equals(grantType)) {
                return userSession;
            }
            if(userId != null && userKind != null && NotiConstant.GRANT_TYPE_PASSWORD.equals(grantType)){
                userSession.setId(userId);
                userSession.setKind(userKind);
                return userSession;
            }
            //System.out.println("userId: "+ decodedJWT.getClaim("user_id"));
            //System.out.println("userKind: "+ decodedJWT.getClaim("user_kind"));
            //System.out.println(decodedJWT);

            return null;

        } catch (Exception e) {
            LOG.error("RSA key: "+publicKey);
            LOG.error("verifierJWT>>"+e.getMessage());
            return null;
        }
    }
}
