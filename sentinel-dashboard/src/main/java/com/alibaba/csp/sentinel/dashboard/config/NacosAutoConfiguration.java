package com.alibaba.csp.sentinel.dashboard.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NacosAutoConfiguration {
    @Value("${nacos.server}")
    private String nacosServer;
    @Bean
    ConfigService configService() throws NacosException {
        ConfigService configService = NacosFactory.createConfigService("http://"+nacosServer);
        return configService;
    }
}
