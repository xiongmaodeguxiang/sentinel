package com.alibaba.csp.nacos;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.List;

public class InitRulesFunc implements InitFunc {

    public static final String APP_NAME = AppNameUtil.getAppName();
    public static final String NACOS_SERVER = SentinelConfig.getConfig("nacos.server");
    public static final String DEFALUT_NACOS_SERVER = "localhost:8848";

    public static final String GROUP_ID = "SENTINEL_GROUP";



    public static ConfigService configService;


    static {
        String serverAddr = DEFALUT_NACOS_SERVER;
        if(!StringUtil.isEmpty(NACOS_SERVER)){
            serverAddr = NACOS_SERVER;
        }
        try {
            configService = NacosFactory.createConfigService("http://"+ serverAddr);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() throws Exception {
        initFlowRules();
        initDegradeRules();
        initParamRules();
        initSystemRules();
        initAuthRules();
        initState();
    }

    private void initState() {
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
    }



    private void initAuthRules() {
        ReadableDataSource<String, List<AuthorityRule>> authRuleData = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.AUTH_RULE_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<AuthorityRule>>(){})
        );
        AuthorityRuleManager.register2Property(authRuleData.getProperty());
    }

    private void initSystemRules() {
        ReadableDataSource<String, List<SystemRule>> systemRuleData = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.SYSTEM_RULE_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<SystemRule>>(){})
        );
        SystemRuleManager.register2Property(systemRuleData.getProperty());
    }

    private void initParamRules() {
        ReadableDataSource<String, List<ParamFlowRule>> paramRuleData = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.PARAM_FLOW_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>(){})
        );
        ParamFlowRuleManager.register2Property(paramRuleData.getProperty());
    }

    private void initDegradeRules() {
        ReadableDataSource<String, List<DegradeRule>> degradeRuleData = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.DEGRADE_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>(){})
        );
        DegradeRuleManager.register2Property(degradeRuleData.getProperty());

    }

    private void initFlowRules() {
        ReadableDataSource<String, List<FlowRule>> flowRuleData = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.FLOW_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>(){})
        );
        FlowRuleManager.register2Property(flowRuleData.getProperty());
    }
}
