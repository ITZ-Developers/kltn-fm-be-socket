package noti.socket.redis;

import noti.common.utils.ConfigurationService;
import noti.socket.constant.NotiConstant;
import noti.socket.constant.RedisConstant;
import noti.socket.model.ClientChannel;
import noti.socket.model.response.DeviceSessionDto;
import noti.socket.redis.tjedis.TJedis;
import noti.socket.redis.tjedis.TJedisAbstractPool;
import noti.socket.redis.tjedis.TJedisPool;
import noti.socket.redis.tjedis.TJedisSentinelPool;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * https://github.com/redisson/redisson
 * https://github.com/redisson/redisson/wiki/Table-of-Content
 * <p>
 * Docker: https://hub.docker.com/r/bitnami/redis-sentinel
 */
public class RedisService {
    private final Logger log = LogManager.getLogger(RedisService.class);
    private static RedisService instance;
    private final TJedis iRedis;
    private final Integer TWO_HOURS = 7200;
    private final SimpleDateFormat DATE_FORMAT;
    /**
     * Keys
     **/
    private static final String PREFIX_KEY_POS = "pos:"; // pos:<tenantId>:<posId>
    private static final String PREFIX_KEY_VIEW = "view:"; // view:<tenantId>:<posId>
    private static final String PREFIX_KEY_EMPLOYEE = "employee:"; // employee:<tenantId>:<posId>

    public String getKeyString(Integer keyType, String tenantName, String posId) {
        if (RedisConstant.KEY_EMPLOYEE.equals(keyType)) {
            return PREFIX_KEY_EMPLOYEE + tenantName + ":" + posId;
        } else if (RedisConstant.KEY_POS.equals(keyType)) {
            return PREFIX_KEY_POS + tenantName + ":" + posId;
        } else {
            return PREFIX_KEY_VIEW + tenantName + ":" + posId;
        }
    }

    private RedisService() {
        ConfigurationService config = new ConfigurationService("configuration.properties");

        int maxSize = config.getInt("server.redis.threads.size");
        int redisType = config.getInt("redis.type");
        String password = config.getConfig("REDIS_PASSWORD", "redis.password");

        TJedisAbstractPool pool;
        if (redisType == 2) {
            String[] sentinelHosts = config.getStringArray("redis.sentinel.host");
            String masterName = config.getString("redis.master.name");
            pool = new TJedisSentinelPool(sentinelHosts, masterName, maxSize, password);
        } else {
            String standAloneHost = config.getString("redis.host");
            pool = new TJedisPool(standAloneHost, maxSize, password);
        }
        this.iRedis = new TJedis(pool);
        this.DATE_FORMAT = new SimpleDateFormat(NotiConstant.DATE_TIME_FORMAT);
    }

    public Jedis getJedis() {
        return iRedis.getJedis();
    }

    public static RedisService getInstance() {
        if (instance == null) {
            instance = new RedisService();
        }
        return instance;
    }

    public void startRedis() {
        iRedis.startRedis();
    }

    private boolean isFieldExistAndHasValue(Jedis jedis, String key, String field, String expectedValue) {
        if (jedis.hexists(key, field)) {
            String fieldValue = jedis.hget(key, field);
            return fieldValue != null && fieldValue.equals(expectedValue);
        }
        return false;
    }

    public Map<String, String> hashGetByKey(Jedis jedis, String key) {
        return jedis.hgetAll(key);
    }

    public boolean isValidSession(String key, String sessionId) {
        Jedis jedis = getJedis();
        try {
            return jedis.exists(key) && isFieldExistAndHasValue(jedis, key, RedisConstant.FIELD_SESSION, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            jedis.close();
        }
    }

    public void handleUpdateTimeLastUsed(String key) {
        Jedis jedis = getJedis();
        String formattedDate = DateTimeFormatter
                .ofPattern(NotiConstant.DATE_TIME_FORMAT)
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        try {
            jedis.hset(key, RedisConstant.FIELD_TIME, formattedDate);
            jedis.expire(key, TWO_HOURS);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }
    }

    public DeviceSessionDto getDeviceDtoByKey(String key) {
        Jedis jedis = getJedis();
        try {
            if (jedis.exists(key)) {
                DeviceSessionDto dto = new DeviceSessionDto();
                Map<String, String> rs = hashGetByKey(jedis, key);
                if (rs != null) {
                    dto.setEmployee(rs.get("employee"));
                    dto.setSession(rs.get("session"));
                    dto.setTime(DATE_FORMAT.parse(rs.get("time")));
                }
                return dto;
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            jedis.close();
        }
    }

    public void removeKey(String key) {
        Jedis jedis = getJedis();
        try {
            if (jedis.exists(key)) {
                jedis.del(key);
                log.info("[Redis] >>> Remove key {}", key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }
    }

    public void handleRemoveKey(ClientChannel channel) {
        Integer keyType = channel.getKeyType();
        String tenantName = channel.getTenantName();
        String posId = channel.getPosId();
        String key = "";
        if (RedisConstant.KEY_EMPLOYEE.equals(keyType)) {
            key = getKeyString(RedisConstant.KEY_EMPLOYEE, tenantName, posId);
        } else if (RedisConstant.KEY_VIEW.equals(keyType)) {
            key = getKeyString(RedisConstant.KEY_VIEW, tenantName, posId);
        } else if (RedisConstant.KEY_POS.equals(keyType)) {
            key = getKeyString(RedisConstant.KEY_POS, tenantName, posId);
            DeviceSessionDto dto = getDeviceDtoByKey(key);
            if (StringUtils.isNotBlank(dto.getEmployee())) {
                String keyEmpl = getKeyString(RedisConstant.KEY_EMPLOYEE, tenantName, posId);
                removeKey(keyEmpl);
            }
        }
        removeKey(key);
    }
}