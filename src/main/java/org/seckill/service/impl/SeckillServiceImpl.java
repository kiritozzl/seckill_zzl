package org.seckill.service.impl;

import org.apache.commons.collections.MapUtils;
import org.seckill.dao.SeckillDao;
import org.seckill.dao.SuccessKilledDao;
import org.seckill.dao.cache.RedisDao;
import org.seckill.dto.Exposer;
import org.seckill.dto.SeckillExecution;
import org.seckill.entity.Seckill;
import org.seckill.entity.SuccessKilled;
import org.seckill.enums.SeckillStateEnum;
import org.seckill.exception.RepeatKillException;
import org.seckill.exception.SeckillCloseException;
import org.seckill.exception.SeckillException;
import org.seckill.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeckillServiceImpl implements SeckillService {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SeckillDao seckillDao;

    @Autowired
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RedisDao redisDao;

    // md5盐值字符串，用于混淆MD5
    private final String slat = "skdfjksjdf7787%^%^%^FSKJFK*(&&%^%&^8DF8^%^^*7hFJDHFJ";

    @Override
    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0, 5);
    }

    @Override
    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }

    private String getMD5(long seckillId){
        String base = seckillId + "/" + slat;
        return DigestUtils.md5DigestAsHex(base.getBytes());
    }

    @Override
    public Exposer exportSeckillUrl(long seckillId) {
        // 优化点：缓存优化：超时的基础上维护一致性
        // 1.访问redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        if (seckill == null){
            // 2.访问数据库
            seckill = seckillDao.queryById(seckillId);
            if(seckill == null){
                return new Exposer(false, seckillId);
            }
            redisDao.putSeckill(seckill);
        }
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        Date nowTime = new Date();
        if((nowTime.getTime() < startTime.getTime() ||
                nowTime.getTime() > endTime.getTime())){
            return new Exposer(false,seckillId,nowTime.getTime(),
                    startTime.getTime(),endTime.getTime());
        }
        String md5 = getMD5(seckillId);
        return new Exposer(true,md5,seckillId);
    }

    @Override
    @Transactional
    /**
     * 使用注解控制事务方法的优点： 1.开发团队达成一致约定，明确标注事务方法的编程风格
     * 2.保证事务方法的执行时间尽可能短，不要穿插其他网络操作，RPC/HTTP请求或者剥离到事务方法外部
     * 3.不是所有的方法都需要事务，如只有一条修改操作，只读操作不需要事务控制
     */
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {
        if(md5 == null || !md5.equals(getMD5(seckillId))){
            throw new SeckillException("seckill data rewrite");
        }
        Date now = new Date();
        try{
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            if(insertCount <= 0){
                throw new RepeatKillException("seckill repeated");
            }else{
                //减库存，热点商品竞争
                int updateCount = seckillDao.reduceNumber(seckillId, now);
                if(updateCount <= 0){
                    //rowback
                    throw new SeckillCloseException("seckill is closed");
                }else{
                    //commit
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId,userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS,successKilled);
                }
            }
        }catch (SeckillCloseException e1){
            throw e1;
        }catch (RepeatKillException e2){
            throw e2;
        }catch (Exception e){
            logger.error("+seckill+"+e.getMessage(), e);
            throw new SeckillException("seckill inner exception: "+e.getMessage());
        }
    }

    @Override
    public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5) {
        if(md5 == null || !md5.equals(getMD5(seckillId))){
            throw new SeckillException("seckill data rewrite");
        }
        Date killTime = new Date();
        Map<String, Object> map = new HashMap<>();
        map.put("seckillId", seckillId);
        map.put("phone", userPhone);
        map.put("killTime", killTime);
        map.put("result", null);
        // 执行存储过程，result被赋值
        try{
            seckillDao.killByProcedure(map);
            // 获取result
            int result = MapUtils.getInteger(map, "result", -2);
            if (result == 1){
                SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, sk);
            }else {
                return new SeckillExecution(seckillId, SeckillStateEnum.stateOf(result));
            }
        }catch (Exception e){
            logger.error("+seckill+"+e.getMessage(), e);
            return new SeckillExecution(seckillId, SeckillStateEnum.INNER_ERROR);
        }
    }
}
