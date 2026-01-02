package com.alander.trip12306.base.safa;

import org.springframework.beans.factory.InitializingBean;

public class FastJsonSafeMode implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("fastjson2.parser.safeMode", "true");
    }
}
