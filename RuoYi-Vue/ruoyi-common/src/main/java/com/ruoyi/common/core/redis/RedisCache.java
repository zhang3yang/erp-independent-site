package com.ruoyi.common.core.redis;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * spring redis 工具类
 *
 * @author ruoyi
 **/
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class RedisCache
{
    private static final DefaultRedisScript<Long> VERIFY_SMS_CODE_SCRIPT = new DefaultRedisScript<>(
            "local failures = tonumber(redis.call('get', KEYS[2]) or '0')\n" +
            "local maxFailures = tonumber(ARGV[2])\n" +
            "if failures >= maxFailures then\n" +
            "    redis.call('del', KEYS[1])\n" +
            "    return -2\n" +
            "end\n" +
            "local expected = redis.call('get', KEYS[1])\n" +
            "if not expected then\n" +
            "    return 0\n" +
            "end\n" +
            "if expected == ARGV[1] then\n" +
            "    redis.call('del', KEYS[1], KEYS[2])\n" +
            "    return 1\n" +
            "end\n" +
            "local nextFailures = redis.call('incr', KEYS[2])\n" +
            "redis.call('expire', KEYS[2], tonumber(ARGV[3]))\n" +
            "if nextFailures >= maxFailures then\n" +
            "    redis.call('del', KEYS[1])\n" +
            "end\n" +
            "return -1",
            Long.class);

    /**
     * 原子「取出并删除」。等价于 Redis 6.2 的 GETDEL，但 2.6+ 就能跑——
     * 现网用的是 Redis 5.x，GETDEL 在那上面不存在。见 {@link #getAndDeleteCacheObject}。
     */
    private static final DefaultRedisScript<Object> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1])\n" +
            "if value then\n" +
            "    redis.call('del', KEYS[1])\n" +
            "end\n" +
            "return value",
            Object.class);

    @Autowired
    public RedisTemplate redisTemplate;

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value)
    {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 仅当 key 不存在时写入带过期时间的缓存，用于限流/冷却标记。
     */
    public <T> boolean setCacheObjectIfAbsent(final String key, final T value, final Integer timeout,
            final TimeUnit timeUnit)
    {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取有效时间
     *
     * @param key Redis键
     * @return 有效时间
     */
    public long getExpire(final String key)
    {
        return redisTemplate.getExpire(key);
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key)
    {
        return redisTemplate.hasKey(key);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }

    /**
     * 原子读取并删除缓存对象，用于一次性凭据（短信验证码、独立站管理台的入场票）。
     *
     * <p>用 Lua 脚本而不是 {@code opsForValue().getAndDelete()}：后者走的是 Redis 的 GETDEL 命令，
     * <b>需要 Redis 6.2+</b>。生产和开发环境上跑的是 Redis 5.x，那里 GETDEL 不存在，
     * 调用会直接抛 {@code RedisCommandExecutionException: ERR unknown command 'GETDEL'}——
     * 表现是「一次性凭据一用就 500」，而且只在真正调用它的功能上线之后才暴露。</p>
     *
     * <p>脚本里的 get + del 在 Redis 单线程里是一次执行，原子性和 GETDEL 完全一样：
     * 两个请求同时拿同一个 key，只有一个拿得到值。<b>这一点是「一次性」的全部保证，
     * 别改成先 get 再 delete 两条命令</b>——那样并发下会双花。</p>
     */
    public <T> T getAndDeleteCacheObject(final String key)
    {
        return (T) redisTemplate.execute(GET_AND_DELETE_SCRIPT, Arrays.asList(key));
    }

    /**
     * 原子校验并消费短信验证码。
     *
     * @return 1=成功，0=不存在或过期，-1=验证码错误，-2=错误次数过多
     */
    public long verifyAndConsumeSmsCode(final String codeKey, final String failKey, final String code,
            final int maxFailures, final int failExpireSeconds)
    {
        Long result = (Long) redisTemplate.execute(VERIFY_SMS_CODE_SCRIPT, Arrays.asList(codeKey, failKey),
                code, maxFailures, failExpireSeconds);
        return result == null ? 0L : result;
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key)
    {
        return redisTemplate.delete(key);
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return
     */
    public boolean deleteObject(final Collection collection)
    {
        return redisTemplate.delete(collection) > 0;
    }

    /**
     * 缓存List数据
     *
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        Long count = redisTemplate.opsForList().rightPushAll(key, dataList);
        return count == null ? 0 : count;
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key)
    {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 缓存Set
     *
     * @param key 缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet)
    {
        BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
        Iterator<T> it = dataSet.iterator();
        while (it.hasNext())
        {
            setOperation.add(it.next());
        }
        return setOperation;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(final String key)
    {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (dataMap != null) {
            redisTemplate.opsForHash().putAll(key, dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key)
    {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 往Hash中存入数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        redisTemplate.opsForHash().put(key, hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(key, hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        return redisTemplate.opsForHash().multiGet(key, hKeys);
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        return redisTemplate.opsForHash().delete(key, hKey) > 0;
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern)
    {
        return redisTemplate.keys(pattern);
    }
}
