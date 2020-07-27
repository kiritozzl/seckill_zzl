package org.seckill.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.seckill.dao.SeckillDao;
import org.seckill.dao.SuccessKilledDao;
import org.seckill.dto.Exposer;
import org.seckill.dto.SeckillExecution;
import org.seckill.entity.Seckill;
import org.seckill.exception.RepeatKillException;
import org.seckill.exception.SeckillCloseException;
import org.seckill.exception.SeckillException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "classpath:spring/spring-dao.xml",
        "classpath:spring/spring-service.xml"
})
public class SeckillServiceTest {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SeckillService seckillService;

    @Test
    public void getSeckillList() {
        List<Seckill> seckillList = seckillService.getSeckillList();
        for(Seckill seckill:seckillList){
            System.out.println(seckill);
        }
    }

    @Test
    public void getById() {
        Seckill seckill = seckillService.getById(1000L);
        System.out.println(seckill);
    }

    @Test
    public void exportSeckillUrl() {
        Exposer exposer = seckillService.exportSeckillUrl(1000L);
        if(exposer.isExposed()){
            logger.info("exposer={}", exposer);
            try{
                SeckillExecution execution = seckillService.executeSeckill(1000L,14853214750L,exposer.getMd5());
                logger.info("execution={}",execution);
            }catch (RepeatKillException e1){
                logger.error(e1.getMessage());
            }catch (SeckillCloseException e2){
                logger.error(e2.getMessage());
            }
        }else{
            //秒杀未开启
            logger.error("exposer={}",exposer);
        }
    }

    @Test
    public void executeSeckill() {
    }
}