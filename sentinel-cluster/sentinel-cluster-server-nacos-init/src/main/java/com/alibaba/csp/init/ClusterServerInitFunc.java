package com.alibaba.csp.init;

import com.alibaba.csp.sentinel.cluster.ClusterServerConfig;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.NacosConfigConstant;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.StringUtils;

import java.net.*;
import java.util.*;

public class ClusterServerInitFunc implements InitFunc {
    public static final String SERVER_TRANSPORT = "server.transport";
    public static final String SERVER_IDEAL_SECOND = "server.ideal";
    public static final String SERVER_NAME_SPACE_SET ="server.namespace.set";

    public static final String DEFAULT_SET ="default";

    public static final String port_s = SentinelConfig.getConfig(SERVER_TRANSPORT);
    public static final String ideal_s = SentinelConfig.getConfig(SERVER_IDEAL_SECOND);
    public static final String namespaceSet_s = SentinelConfig.getConfig(SERVER_NAME_SPACE_SET);

    public static String NACOS_ADDR = "localhost:8848";
    public static final String APP_NAME = AppNameUtil.getAppName();
    public static final String NAME_SPACE_SET_DATAID_SUFFIX = "-namespace-set";

    public static final String NACOS_NAMESPACE = "SENTINEL_GROUP";

    public static ConfigService configService;

    public int port = 18730;
    public int idleSeconds = 600;
    public static String IP;
    public Set<String> namespaceSet = new HashSet<>();
    public Set<String> oldNamespaceSet = new HashSet<>();

    @Override
    public void init() throws Exception {
        parseConfig();
        initConfigToNacos();
        initClusterFlowSupplier();
        initClusterParamFlowSupplier();
        registerServerTransPortConfigListener();
        registerNameSpaceSetListener();
        initState();
    }
    /**
     * 初始化集群限流的Supplier
     * 这样如果后期集群限流的规则发生变更的话，系统可以自动感知到
     */
    private void initClusterFlowSupplier() {
        // 为集群流控注册一个Supplier，该Supplier会根据namespace动态创建数据源
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            // 使用 Nacos 数据源作为配置中心，需要在 REMOTE_ADDRESS 上启动一个 Nacos 的服务
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(NACOS_ADDR, NACOS_NAMESPACE,
                    namespace + NacosConfigConstant.FLOW_DATA_ID_POSTFIX,
                    source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            return ds.getProperty();
        });
    }

    /**
     * 初始化集群热点参数限流的Supplier
     * 这样如果后期集群热点参数限流的规则发生变更的话，系统可以自动感知到
     */
    public void initClusterParamFlowSupplier() {
        // 为集群热点参数流控注册一个Supplier，该Supplier会根据namespace动态创建数据源
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            // 使用 Nacos 数据源作为配置中心，需要在 REMOTE_ADDRESS 上启动一个 Nacos 的服务
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(NACOS_ADDR, NACOS_NAMESPACE,
                    namespace + NacosConfigConstant.PARAM_FLOW_DATA_ID_POSTFIX,
                    source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
            return ds.getProperty();
        });
    }



    private void initState() {
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_SERVER);
    }
    private void initConfigToNacos() {
        //读取nacos中配置的namespaceSet
        try {
            String nameSpaceSetConfig = configService.getConfig(APP_NAME + NAME_SPACE_SET_DATAID_SUFFIX, NACOS_NAMESPACE, 5000);
            if(StringUtils.isEmpty(nameSpaceSetConfig)){
                configService.publishConfig(APP_NAME + NAME_SPACE_SET_DATAID_SUFFIX, NACOS_NAMESPACE, JSON.toJSONString(namespaceSet));
            }else{
                nameSpaceSetConfig = configService.getConfig(APP_NAME + NAME_SPACE_SET_DATAID_SUFFIX, NACOS_NAMESPACE, 5000);
                namespaceSet = JSON.parseObject(nameSpaceSetConfig,new TypeReference<Set<String>>() {});
            }
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
        //读取nacos中配置的ClusterTransportConfig
        try {
            String transportConfigStr = configService.getConfig(APP_NAME + NacosConfigConstant.SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX, NACOS_NAMESPACE, 5000);
            if(StringUtils.isEmpty(transportConfigStr)){
                ServerTransportConfig serverTransportConfig = new ServerTransportConfig();
                serverTransportConfig.setPort(port);
                serverTransportConfig.setIdleSeconds(idleSeconds);
                configService.publishConfig(APP_NAME + NacosConfigConstant.SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX, NACOS_NAMESPACE, JSON.toJSONString(serverTransportConfig));
            }else{
                ServerTransportConfig transportConfig = JSON.parseObject(transportConfigStr, ServerTransportConfig.class);
                port = transportConfig.getPort();
            }
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }
    private void registerNameSpaceSetListener() {
        ReadableDataSource<String, Set<String>> nameSetDataSource = new NacosDataSource<Set<String>>(NACOS_ADDR, NACOS_NAMESPACE, APP_NAME + NAME_SPACE_SET_DATAID_SUFFIX,
                source -> {
                    Set<String> nameSet = JSON.parseObject(source, new TypeReference<Set<String>>() {});
                    nameSpaceSetChanged(nameSet);
                    return nameSet;
                });
        ClusterServerConfigManager.registerNamespaceSetProperty(nameSetDataSource.getProperty());
    }

    private void nameSpaceSetChanged(Set<String> nameSet) {
        namespaceSet = nameSet;
        nameSet.stream().forEach(item ->{
            if (!oldNamespaceSet.contains(item)) {
                oldNamespaceSet.add(item);
                publishTransportConfigToNacos(IP, port, idleSeconds, item);
            }
        });

    }

    private void publishTransportConfigToNacos(String ip, int port, int ideal, String appName) {
        ClusterServerConfig clusterServerConfig = new ClusterServerConfig();
        clusterServerConfig.setServerIP(ip);
        clusterServerConfig.setPort(port);
        try {
            configService.publishConfig(appName + NacosConfigConstant.CLIENT_ASSIGN_CONFIG_DATA_ID_POSTFIX, NACOS_NAMESPACE, JSON.toJSONString(clusterServerConfig));
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseConfig() {
        if (!StringUtils.isEmpty(port_s)) {
            port = Integer.parseInt(port_s);
        }
        if(!StringUtils.isEmpty(ideal_s)){
            idleSeconds = Integer.parseInt(ideal_s);
        }
        if(!StringUtils.isEmpty(namespaceSet_s)){
            String[] namespaceArr = namespaceSet_s.split(",");
            Arrays.asList(namespaceArr).stream().forEach(item -> namespaceSet.add(item));
        }else if(!StringUtils.isEmpty(APP_NAME)){
            namespaceSet.add(APP_NAME);
        }else{
            namespaceSet.add(DEFAULT_SET);
        }
    }

    private void registerServerTransPortConfigListener() {
        ReadableDataSource<String, ServerTransportConfig> clusterTransPortConfigDataSource = new NacosDataSource<>(NACOS_ADDR, NACOS_NAMESPACE, APP_NAME + NacosConfigConstant.SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX,
                source -> {
                    ServerTransportConfig serverTransportConfig =   JSON.parseObject(source, new TypeReference<ServerTransportConfig>() {});
                    serverTransportConfigChanged(serverTransportConfig);
                    return serverTransportConfig;
                });

        ClusterServerConfigManager.registerServerTransportProperty(clusterTransPortConfigDataSource.getProperty());
    }

    private void serverTransportConfigChanged(ServerTransportConfig serverTransportConfig) {
        port = serverTransportConfig.getPort();
        idleSeconds = serverTransportConfig.getIdleSeconds();
        namespaceSet.forEach(item -> {
            publishTransportConfigToNacos(IP,serverTransportConfig.getPort(), serverTransportConfig.getIdleSeconds(), item);
        });
    }

    private static String getIP() throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getLocalHost();
        String ip = address.getHostAddress();
        if(address.isLoopbackAddress()){
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()){
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()){
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if(!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address){
                        ip = inetAddress.getHostAddress();
                    }
                }
            }
        }
        return ip;
    }

    static {
        String nacosServer = SentinelConfig.getConfig("nacos.server");
        if(!StringUtils.isEmpty(nacosServer)){
            NACOS_ADDR = nacosServer;
        }
        try {
            configService = NacosFactory.createConfigService("http://"+NACOS_ADDR);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
        try {
            IP = getIP();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }
}
