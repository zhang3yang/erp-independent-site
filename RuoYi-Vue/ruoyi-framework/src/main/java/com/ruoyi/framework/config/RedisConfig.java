package com.ruoyi.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * redis配置
 * 
 * @author ruoyi
 */
@SuppressWarnings("deprecation")
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport
{
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:10s}")
    private java.time.Duration redisTimeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int redisPoolMaxTotal;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int redisPoolMaxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int redisPoolMinIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private java.time.Duration redisPoolMaxWait;

    /**
     * Jedis connection pool used by the legacy RedisService.
     * It shares the same spring.data.redis connection settings as Spring Data Redis.
     */
    @Bean(destroyMethod = "close")
    public JedisPool jedisPool()
    {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Jedis Commons Pool registers an MXBean named "pool2" by default.
        // Spring Boot also exports the JedisPool bean through JMX, which causes
        // InstanceAlreadyExistsException during application startup.
        poolConfig.setJmxEnabled(false);
        poolConfig.setMaxTotal(redisPoolMaxTotal);
        poolConfig.setMaxIdle(redisPoolMaxIdle);
        poolConfig.setMinIdle(redisPoolMinIdle);
        poolConfig.setMaxWaitMillis(redisPoolMaxWait.toMillis());

        String password = redisPassword == null || redisPassword.isEmpty() ? null : redisPassword;
        int timeoutMillis = Math.toIntExact(redisTimeout.toMillis());
        return new JedisPool(poolConfig, redisHost, redisPort, timeoutMillis, password, redisDatabase);
    }

    @Bean
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory)
    {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        // Hash的key也采用StringRedisSerializer的序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public DefaultRedisScript<Long> limitScript()
    {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(limitScriptText());
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * 限流脚本
     */
    private String limitScriptText()
    {
        return "local key = KEYS[1]\n" +
                "local count = tonumber(ARGV[1])\n" +
                "local time = tonumber(ARGV[2])\n" +
                "local current = redis.call('get', key);\n" +
                "if current and tonumber(current) > count then\n" +
                "    return tonumber(current);\n" +
                "end\n" +
                "current = redis.call('incr', key)\n" +
                "if tonumber(current) == 1 then\n" +
                "    redis.call('expire', key, time)\n" +
                "end\n" +
                "return tonumber(current);";
    }
}
