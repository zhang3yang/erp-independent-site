package com.ruoyi.common.redis;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Service
public class RedisService {
    private static final String LOCK_SUCCESS = "OK";
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    JedisPool jedisPool;

    public Map getLock(String lockKey, int time) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String requestId = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + UUID.randomUUID().toString().replace("-", "").toLowerCase();
            boolean temp = false;
            while (!temp) {
                temp = tryGetDistributedLock(jedis, lockKey, requestId, time);
                if (temp) {
                    map.put("result", true);
                    map.put("requestId", requestId);
                    return map;
                }
                Thread.sleep(10);
                continue;
            }
            return map;
        } catch (Exception e) {
            map.put("result", false);
            map.put("msg", e.getMessage());
            return map;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public Map templock(String lockKey, int time) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String requestId = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + UUID.randomUUID().toString().replace("-", "").toLowerCase();
            boolean temp = false;
            //临时锁不用解锁，锁会随时间自动解锁，所以可以在函数里面在前面增加前缀，不影响外部使用。
            temp = tryGetDistributedLock(jedis, "templock:" + lockKey, requestId, time);
            if (temp) {
                map.put("result", true);
                return map;
            }
            map.put("result", false);
            return map;
        } catch (Exception e) {
            map.put("result", false);
            map.put("msg", e.getMessage());
            return map;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public String releaseLock(String lockKey, String requestId) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            boolean temp = releaseDistributedLock(jedis, lockKey, requestId);
            if (temp) {
                return requestId;
            } else {
                System.out.println(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "[" + lockKey + "][" + requestId + "]解锁失败");
                return "";
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "";
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * 在限定等待时间内尝试获取分布式锁。返回结果中的 requestId 只能由当前持有者用于释放锁。
     */
    public Map tryLock(String lockKey, long waitTimeMillis, int leaseTimeMillis) {
        Map map = new HashMap();
        if (lockKey == null || lockKey.trim().isEmpty()) {
            map.put("result", false);
            map.put("msg", "锁Key不能为空");
            return map;
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, waitTimeMillis);
        String requestId = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + UUID.randomUUID().toString().replace("-", "").toLowerCase();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            do {
                if (tryGetDistributedLock(jedis, lockKey, requestId, leaseTimeMillis)) {
                    map.put("result", true);
                    map.put("requestId", requestId);
                    return map;
                }
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                Thread.sleep(Math.min(20L, Math.max(1L, deadline - System.currentTimeMillis())));
            } while (System.currentTimeMillis() <= deadline);
            map.put("result", false);
            map.put("msg", "获取库存分布式锁超时");
            return map;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            map.put("result", false);
            map.put("msg", "获取库存分布式锁被中断");
            return map;
        } catch (Exception e) {
            map.put("result", false);
            map.put("msg", e.getMessage());
            return map;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public Map geDayKey(KeyType keyType, String prefix)  {
        Map map1 = new HashMap();
        String lockKey = "dayLock";
        Map map = getLock(lockKey, 10000);
        if (!(boolean) map.get("result")) {
            return map;
        }
        Jedis jedis = null;
        //生成主键需要的值
        /* String str = stringRedisTemplate.opsForValue().get("keyCreateByDay");*/
        try {
            jedis = jedisPool.getResource();
            String str = jedis.get("keyCreateByDay");
            Map<String, String> maps = str == null || str.trim().isEmpty()
                    ? new HashMap<>() : (Map) JSON.parse(str);
            if (maps == null)
            {
                maps = new HashMap<>();
            }
            maps.putIfAbsent("genid", "1");
            //上一次主键获取时间
            String lastDay = maps.get("keytime");
            //当前时间
            String curDay = DateUtil.getDay();
            //序列号
            Integer serialno = 1;

            //如果上一个主键和当前主键获取时间相同则更新序列号+1
            if (lastDay != null && DateUtil.ifEqDate(lastDay, curDay) && maps.get("serialno") != null) {
                serialno = Integer.parseInt(maps.get("serialno")) + 1;
                maps.put("serialno", serialno.toString());
            } else {
                //如果上一个主键和当前主键获取时间不同则更新时间序列号初始化为1
                maps.put("keytime", curDay);
                maps.put("serialno", serialno.toString());
            }
            /*stringRedisTemplate.opsForValue().set("keyCreateByDay", JSON.toJSONString(maps));*/
            jedis.set("keyCreateByDay", JSON.toJSONString(maps));
            releaseLock(lockKey, map.get("requestId").toString());

            int ServiceNo = new Integer(maps.get("genid"));
            long daycount = DateUtil.getDaySub(ConstSelf.BASE_DATE, curDay);
            String svrstr = KeyCreatorUtil.valuetoString((long) ServiceNo, keyType);
            String daystr = KeyCreatorUtil.valuetoString(daycount, keyType);
            String serstr = KeyCreatorUtil.valuetoString((long) serialno, keyType);
            String keyvalue = svrstr + "0" + daystr + "0" + serstr;
            map1.put("result", true);
            map1.put("primaryKey", prefix + keyvalue);
            return map1;
        } catch (Exception e) {
            map1.put("result", false);
            map1.put("errorinfo", e.getMessage());
            return map1;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public Map geHourKey(KeyType keyType, String prefix) {
        Map map1 = new HashMap();
        String lockKey = "hourLock";
        /*long sss1 = System.currentTimeMillis();*/
        Map map = getLock(lockKey, 10000);
        /*long sss2 = System.currentTimeMillis();*/

        if (!Boolean.TRUE.equals(map.get("result"))) {
            return map;
        }
        String requestId = String.valueOf(map.get("requestId"));
        Jedis jedis = null;
        //生成主键需要的值
        /*String str = stringRedisTemplate.opsForValue().get("keyCreateBySeconds");*/
        /*long sss3 = System.currentTimeMillis();*/
        try {
            jedis = jedisPool.getResource();
            String str = jedis.get("keyCreateByHour");
            /*long sss4 = System.currentTimeMillis();*/
            Map<String, String> maps = str == null || str.trim().isEmpty()
                    ? new HashMap<>() : (Map) JSON.parse(str);
            if (maps == null)
            {
                maps = new HashMap<>();
            }
            maps.putIfAbsent("genid", "1");
            //上一次主键获取时间
            String lastTime = maps.get("keytime");
            //当前时间
            String curHour = DateUtil.getHour();
            //序列号
            Integer serialno = 1;
            //如果上一个主键和当前主键获取时间相同则更新序列号+1
            if (lastTime != null && DateUtil.ifEqHour(lastTime, curHour) && maps.get("serialno") != null) {
                serialno = Integer.parseInt(maps.get("serialno")) + 1;
                maps.put("serialno", serialno.toString());
            } else {
                //如果上一个主键和当前主键获取时间不同则更新时间序列号初始化为1
                maps.put("keytime", curHour);
                maps.put("serialno", serialno.toString());
            }
            long s = System.currentTimeMillis();
            /*long sss5 = System.currentTimeMillis();*/
            /*stringRedisTemplate.opsForValue().set("keyCreateBySeconds", JSON.toJSONString(maps));*/
            jedis.set("keyCreateByHour", JSON.toJSONString(maps));
            /*long sss6 = System.currentTimeMillis();*/

            int ServiceNo = Integer.parseInt(maps.get("genid"));
            long daycount = DateUtil.getHourSub(ConstSelf.BASE_TIME, curHour);
            String svrstr = KeyCreatorUtil.valuetoString((long) ServiceNo, keyType);
            String daystr = KeyCreatorUtil.valuetoString(daycount, keyType);
            String serstr = KeyCreatorUtil.valuetoString((long) serialno, keyType);
            String keyvalue = svrstr + "0" + daystr + "0" + serstr;
            map1.put("result", true);
            map1.put("primaryKey", prefix + keyvalue);
            long sss7 = System.currentTimeMillis();
            /*map1.put("time","newsss3:"+(sss6-sss5)+"    newsss1:"+(sss2-sss1)+"    newsss2:"+(sss4-sss3)+"sss31:"+(sss1-sss1)+"     sss2:"+(sss2-sss1)+"     sss3:"+(sss3-sss1)+"     sss4:"+(sss4-sss1)+"     sss5:"+(sss5-sss1)+"     sss6:"+(sss6-sss1)+"     sss7:"+(sss7-sss1));*/
            return map1;
        } catch (Exception e) {
            map1.put("result", false);
            map1.put("errorinfo", e.getMessage());
            return map1;
        } finally {
            if (jedis != null) {
                releaseDistributedLock(jedis, lockKey, requestId);
                jedis.close();
            }
        }
    }

    public Map getSecondKey(KeyType keyType, String prefix) {
        Map map1 = new HashMap();
        String lockKey = "secondLock";
        /*long sss1 = System.currentTimeMillis();*/
        Map map = getLock(lockKey, 10000);
        /*long sss2 = System.currentTimeMillis();*/

        if (!Boolean.TRUE.equals(map.get("result"))) {
            return map;
        }
        String requestId = String.valueOf(map.get("requestId"));
        Jedis jedis = null;
        //生成主键需要的值
        /*String str = stringRedisTemplate.opsForValue().get("keyCreateBySeconds");*/
        /*long sss3 = System.currentTimeMillis();*/
        try {
            jedis = jedisPool.getResource();
            String str = jedis.get("keyCreateBySecond");
            /*long sss4 = System.currentTimeMillis();*/
            Map<String, String> maps = str == null || str.trim().isEmpty()
                    ? new HashMap<>() : (Map) JSON.parse(str);
            if (maps == null)
            {
                maps = new HashMap<>();
            }
            maps.putIfAbsent("genid", "1");
            //上一次主键获取时间
            String lastTime = maps.get("keytime");
            //当前时间
            String curTime = DateUtil.getTime();
            //序列号
            Integer serialno = 1;
            //如果上一个主键和当前主键获取时间相同则更新序列号+1
            if (lastTime != null && DateUtil.ifEqDateTime(lastTime, curTime) && maps.get("serialno") != null) {
                serialno = Integer.parseInt(maps.get("serialno")) + 1;
                maps.put("serialno", serialno.toString());
            } else {
                //如果上一个主键和当前主键获取时间不同则更新时间序列号初始化为1
                maps.put("keytime", curTime);
                maps.put("serialno", serialno.toString());
            }
            long s = System.currentTimeMillis();
            /*long sss5 = System.currentTimeMillis();*/
            /*stringRedisTemplate.opsForValue().set("keyCreateBySeconds", JSON.toJSONString(maps));*/
            jedis.set("keyCreateBySecond", JSON.toJSONString(maps));
            /*long sss6 = System.currentTimeMillis();*/

            int ServiceNo = Integer.parseInt(maps.get("genid"));
            long daycount = DateUtil.getSecondSub(ConstSelf.BASE_TIME, curTime);
            String svrstr = KeyCreatorUtil.valuetoString((long) ServiceNo, keyType);
            String daystr = KeyCreatorUtil.valuetoString(daycount, keyType);
            String serstr = KeyCreatorUtil.valuetoString((long) serialno, keyType);
            String keyvalue = svrstr + "0" + daystr + "0" + serstr;
            map1.put("result", true);
            map1.put("primaryKey", prefix + keyvalue);
            long sss7 = System.currentTimeMillis();
            /*map1.put("time","newsss3:"+(sss6-sss5)+"    newsss1:"+(sss2-sss1)+"    newsss2:"+(sss4-sss3)+"sss31:"+(sss1-sss1)+"     sss2:"+(sss2-sss1)+"     sss3:"+(sss3-sss1)+"     sss4:"+(sss4-sss1)+"     sss5:"+(sss5-sss1)+"     sss6:"+(sss6-sss1)+"     sss7:"+(sss7-sss1));*/
            return map1;
        } catch (Exception e) {
            map1.put("result", false);
            map1.put("errorinfo", e.getMessage());
            return map1;
        } finally {
            if (jedis != null) {
                releaseDistributedLock(jedis, lockKey, requestId);
                jedis.close();
            }
        }
    }

    public Map setValue(String redisKey, String value) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String stat = jedis.set(redisKey, value,
                    SetParams.setParams().nx().px(60L * 60 * 1000 * 24 * 7));
            if(!"OK".equalsIgnoreCase(stat)){
                map.put("result", "fail");
                map.put("errorinfo", "权限未即时生效，重新登陆时生效");
                return map;
            }
            map.put("result", "success");
            return map;
        }catch (Exception e){
            map.put("result", "fail");
            map.put("errorinfo", e.getMessage());
            return map;
        }finally {
            if(jedis!=null){
                jedis.close();
            }
        }
    }
    public Map delValue(String redisKey) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.del(redisKey);
            map.put("result", "success");
            return map;
        }catch (Exception e){
            map.put("result", "fail");
            map.put("errorinfo", e.getMessage());
            return map;
        }finally {
            if(jedis!=null){
                jedis.close();
            }
        }
    }

    public Map getValue(String redisKey) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String value = jedis.get(redisKey);
            map.put("result", "success");
            map.put("value", value);
            return map;
        }catch (Exception e){
            map.put("result", "fail");
            map.put("errorinfo", e.getMessage());
            return map;
        }finally {
            if(jedis!=null){
                jedis.close();
            }
        }
    }

    public Map setValueCover(String redisKey, String value) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String stat = jedis.set(redisKey,value);
            if(!"OK".equalsIgnoreCase(stat)){
                map.put("result", "fail");
                map.put("errorinfo", "添加失败");
                return map;
            }
            map.put("result", "success");
            return map;
        }catch (Exception e){
            map.put("result", "fail");
            map.put("errorinfo", e.getMessage());
            return map;
        }finally {
            if(jedis!=null){
                jedis.close();
            }
        }
    }

    public Map setWorkCode(String redisKey, String value) {
        Map map = new HashMap();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String stat = jedis.set(redisKey, value,
                    SetParams.setParams().nx().px(60L * 60 * 1000 * 3));
            if(!"OK".equalsIgnoreCase(stat)){
                map.put("result", "fail");
                map.put("errorinfo", "权限未即时生效，重新登陆时生效");
                return map;
            }
            map.put("result", "success");
            return map;
        }catch (Exception e){
            map.put("result", "fail");
            map.put("errorinfo", e.getMessage());
            return map;
        }finally {
            if(jedis!=null){
                jedis.close();
            }
        }
    }
    /**
     * 尝试获取分布式锁
     *
     * @param jedis      Redis客户端
     * @param lockKey    锁
     * @param requestId  请求标识
     * @param expireTime 超期时间
     * @return 是否获取成功
     */
    public static boolean tryGetDistributedLock(Jedis jedis, String lockKey, String requestId, int expireTime) {
        String result = jedis.set(lockKey, requestId, SetParams.setParams().nx().px(expireTime));
        if (LOCK_SUCCESS.equals(result)) {
            return true;
        }
        return false;

    }
    /**
     * 释放分布式锁
     *
     * @param jedis     Redis客户端
     * @param lockKey   锁
     * @param requestId 请求标识
     * @return 是否释放成功
     */
    public static boolean releaseDistributedLock(Jedis jedis, String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
        if ("1".equals(result.toString())) {
            return true;
        }
        return false;

    }
}
