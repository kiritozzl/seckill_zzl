package org.seckill.enums;

public enum SeckillStateEnum {
    SUCCESS(1, "秒杀成功"), END(0,"秒杀结束"),
    REPEATE_KILL(1,"重复秒杀"), INNER_ERROR(2,"内部错误"),
    DATA_REWRITE(3,"数据重写");

    private int state;

    private String stateInfo;

    SeckillStateEnum(int state, String stateInfo) {
        this.state = state;
        this.stateInfo = stateInfo;
    }

    public int getState() {
        return state;
    }

    public String getStateInfo() {
        return stateInfo;
    }

    public static SeckillStateEnum stateOf(int index){
        for(SeckillStateEnum stateEnum:values()){
            if(stateEnum.getState() == index){
                return stateEnum;
            }
        }
        return null;
    }
}
