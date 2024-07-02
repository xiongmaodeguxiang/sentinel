package com.alibaba.csp.client.init;

import com.alibaba.csp.sentinel.cluster.ClusterServerConfig;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.NacosConfigConstant;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
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


    public static final String CLUSTER_MAP_DATA_ID_POSTFIX = "-cluster-map";

    public static ConfigService configService;

    /**
     * cc for `cluster-client`
     */
    public static final String CLIENT_CONFIG_DATA_ID_POSTFIX = "-cc-config";
    private static final int REQUEST_TIME_OUT = 200;
    /**
     * cs for `cluster-server`
     */
//    public static final String SERVER_FLOW_CONFIG_DATA_ID_POSTFIX = "-cs-flow-config";
//    public static final String SERVER_NAMESPACE_SET_DATA_ID_POSTFIX = "-cs-namespace-set";

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
        initClusterClientConfig();
        initClusterClientAssignConfig();
        initState();
    }

    private void initState() {
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
    }

    private void initClusterClientConfig() {
        try {
            String clientConfig = configService.getConfig(APP_NAME + CLIENT_CONFIG_DATA_ID_POSTFIX, GROUP_ID, 5000);
            if(StringUtil.isEmpty(clientConfig)){
                ClusterClientConfig clusterClientConfig = new ClusterClientConfig();
                clusterClientConfig.setRequestTimeout(REQUEST_TIME_OUT);
                configService.publishConfig(APP_NAME + CLIENT_CONFIG_DATA_ID_POSTFIX, GROUP_ID, JSON.toJSONString(clusterClientConfig));
            }
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
        ReadableDataSource<String, ClusterClientConfig> clusterClientConfig = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + CLIENT_CONFIG_DATA_ID_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<ClusterClientConfig>(){})
        );
        ClusterClientConfigManager.registerClientConfigProperty(clusterClientConfig.getProperty());
    }

    private void initClusterClientAssignConfig() {
        ReadableDataSource<String, ClusterClientAssignConfig> clusterClientAssignConfig = new NacosDataSource<>(
                NACOS_SERVER, GROUP_ID, APP_NAME + NacosConfigConstant.CLIENT_ASSIGN_CONFIG_DATA_ID_POSTFIX,
                source -> {
                    ClusterServerConfig clusterServerConfig = JSON.parseObject(source, new TypeReference<ClusterServerConfig>(){});
                    ClusterClientAssignConfig clientAssignConfig = new ClusterClientAssignConfig();
                    clientAssignConfig.setServerHost(clusterServerConfig.getServerIP());
                    clientAssignConfig.setServerPort(clusterServerConfig.getPort());
                    return clientAssignConfig;
                }
        );
        ClusterClientConfigManager.registerServerAssignProperty(clusterClientAssignConfig.getProperty());
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
