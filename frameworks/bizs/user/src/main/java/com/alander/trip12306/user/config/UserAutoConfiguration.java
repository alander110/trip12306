package com.alander.trip12306.user.config;

import com.alander.trip12306.base.constant.FilterOrderConstant;
import com.alander.trip12306.user.core.UserTransmitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@ConditionalOnWebApplication
public class UserAutoConfiguration {

    /**
     * 用户信息传递过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter() {
        FilterRegistrationBean<UserTransmitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserTransmitFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(FilterOrderConstant.USER_TRANSMIT_FILTER_ORDER);
        return registration;
    }
}
