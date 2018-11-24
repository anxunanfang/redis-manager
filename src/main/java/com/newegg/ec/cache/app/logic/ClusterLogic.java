package com.newegg.ec.cache.app.logic;

import com.newegg.ec.cache.app.component.RedisManager;
import com.newegg.ec.cache.app.dao.IClusterDao;
import com.newegg.ec.cache.app.dao.impl.NodeInfoDao;
import com.newegg.ec.cache.app.model.*;
import com.newegg.ec.cache.app.util.*;
import com.newegg.ec.cache.core.logger.CommonLogger;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gl49 on 2018/4/21.
 */
@Component
public class ClusterLogic {
    private static CommonLogger logger = new CommonLogger(ClusterLogic.class);

    @Value("${cache.redis.client}")
    private String redisClient;

    @Autowired
    private IClusterDao clusterDao;
    @Autowired
    private NodeInfoDao nodeInfoTable;
    @Resource
    private RedisManager redisManager;

    public Cluster getCluster(int id) {
        return clusterDao.getCluster(id);
    }

    public Object query( RedisQueryParam redisQueryParam) {
        return redisManager.query(redisQueryParam);
    }

    public Map<String, List<Cluster>> getClusterMap(String group) {
        Map<String, List<Cluster>> clusterMap = new HashedMap();
        if (group.equals(Common.ADMIN_GROUP)) {
            List<String> groups = clusterDao.getClusterGroups();
            for (String groupStr : groups) {
                clusterMap.put(groupStr, clusterDao.getClusterList(groupStr));
            }
        }
        clusterMap.put(group, clusterDao.getClusterList(group));
        return clusterMap;
    }

    public boolean clusterExistAddress(String address) {
        List<Cluster> list = clusterDao.getClusterByAddress(address);
        if (list.isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean removeCluster(int id) {
        boolean res = false;
        try {
            clusterDao.removeCluster(id);
            String tableName = Common.NODE_INFO_TABLE_FORMAT + id;
            nodeInfoTable.dropTable(tableName);
            res = true;
        } catch (Exception e) {

        }
        return res;
    }

    public Map<String, Integer> getClusterListInfo(String userGroup) {
        Map<String, Integer> clusterListInfo = new HashMap<>();
        int clusterOkNumber = 0;
        int clusterFailNumber = 0;
        if (StringUtils.isNotBlank(userGroup)) {
            List<Cluster> clusterList = clusterDao.getClusterList(userGroup);
            if (clusterList != null && clusterList.size() > 0) {
                clusterListInfo.put(Common.CLUSTER_NUMBER, clusterList.size());
                for (Cluster cluster : clusterList) {
                    if (getClusterState(cluster.getId())) {
                        clusterOkNumber++;
                    } else {
                        clusterFailNumber++;
                    }
                }
                clusterListInfo.put(Common.CLUSTER_OK_NUMBER, clusterOkNumber);
                clusterListInfo.put(Common.CLUSTER_FAIL_NUMBER, clusterFailNumber);
            } else {
                clusterListInfo.put(Common.CLUSTER_NUMBER, 0);
                clusterListInfo.put(Common.CLUSTER_OK_NUMBER, 0);
                clusterListInfo.put(Common.CLUSTER_FAIL_NUMBER, 0);
            }
        }
        return clusterListInfo;
    }

    public int addCluster(Cluster cluster) {
        int res = -1;
        try {
            int row = clusterDao.addCluster(cluster);
            if (row > 0) {
                cluster.setAddress(cluster.getAddress());
                cluster.setUserGroup(cluster.getUserGroup());
                cluster.setClusterName(cluster.getClusterName());
                nodeInfoTable.createTable(Common.NODE_INFO_TABLE_FORMAT + cluster.getId());
                res = cluster.getId();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    public Map<String, String> getClusterInfo(int clusterId, String ip, int port) {
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(ip, port, cluster.getRedisPassword());
        return redisManager.getClusterInfo(param);
    }

    public Map<String, String> getClusterInfo(int clusterId, String address) {
        List<Host> host = NetUtil.getHostByAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.get(0).getIp(), host.get(0).getPort(), cluster.getRedisPassword());
        return redisManager.getClusterInfo(param);
    }

    public Map<String, String> getNodeInfo(int clusterId, String address) {
        Host host = NetUtil.getHostPassAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.getIp(), host.getPort(), cluster.getRedisPassword());
        return redisManager.getMapInfo(param);
    }

    public Map<String, String> getRedisConfig(int clusterId, String address) {
        Host host = NetUtil.getHostPassAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.getIp(), host.getPort(), cluster.getRedisPassword());
        return redisManager.getRedisConfig(param);
    }

    public List<Map<String, String>> nodeList(int clusterId, String address) {
        Host host = NetUtil.getHostPassAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.getIp(), host.getPort(), cluster.getRedisPassword());
        List<Map<String, String>> list = redisManager.nodeList(param);
        return list;
    }

    public Host getClusterHost(int id) {
        Cluster cluster = getCluster(id);
        String addressStr = cluster.getAddress();
        Host host = NetUtil.getHostPassAddress(addressStr);
        return host;
    }

    public boolean getClusterState(int clusterId) {
        boolean res = false;
        try {
            Host host = getClusterHost(clusterId);
            final Map<String, String> clusterInfo = getClusterInfo(clusterId, host.getIp(), host.getPort());
            String state = clusterInfo.get(Common.CLUSTER_STATE);
            if ("ok".equals(state)) {
                res = true;
            }
        } catch (Exception ignore) {
            // ignore
        }
        return res;
    }

    public Map<String, Map> detailNodeList(int clusterId, String address) {
        Host host = NetUtil.getHostPassAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.getIp(), host.getPort(), cluster.getRedisPassword());
        Map<String, Map> result = redisManager.getClusterNodes(param);
        return result;
    }

    public List<Map<String, String>> getRedisDBList(int clusterId, String address) {
        Host host = NetUtil.getHostPassAddress(address);
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(host.getIp(), host.getPort(), cluster.getRedisPassword());
        return redisManager.getRedisDBList(param);
    }

    public List<Cluster> getClusterListByUser(User user) {
        List<Cluster> listCluster = getClusterList(user.getUserGroup());
        return listCluster;
    }

    public List<Cluster> getClusterList(String group) {
        List<Cluster> clusterList = new ArrayList<>();
        if (group.equals(Common.ADMIN_GROUP)) {
            List<String> groups = clusterDao.getClusterGroups();
            for (String groupStr : groups) {
                clusterList.addAll(clusterDao.getClusterList(groupStr));
            }
        } else {
            clusterList.addAll(clusterDao.getClusterList(group));
        }
        return clusterList;
    }

    public List<Cluster> getClusterListByGroup(String group) {
        List<Cluster> clusterList = new ArrayList<>();
        clusterList.addAll(clusterDao.getClusterList(group));
        return clusterList;
    }

    public boolean beSlave(int clusterId, String ip, int port, String masterId) {
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(ip, port, cluster.getRedisPassword());
        return redisManager.beSlave(param, masterId);
    }

    public boolean beMaster(int clusterId, String ip, int port) {
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(ip, port, cluster.getRedisPassword());
        return redisManager.beMaster(param);
    }

    public boolean forgetNode(int clusterId, String ip, int port, String nodeId) {
        Cluster cluster = getCluster(clusterId);
        ConnectionParam param = new ConnectionParam(ip, port, cluster.getRedisPassword());
        return redisManager.forget(param, nodeId);
    }

    public boolean importNode(int clusterId, String ip, int port, String masterIP, int masterPort) {
        Cluster cluster = getCluster(clusterId);
        ConnectionParam slaveParam = new ConnectionParam(ip, port, cluster.getRedisPassword());
        return redisManager.clusterMeet(slaveParam, masterIP, masterPort);
    }

    public boolean batchConfig(int clusterId, String myIp, int myPort, String configName, String configValue) {
        boolean res = true;
        ConnectionParam param = new ConnectionParam(myIp, myPort);
        Cluster cluster = getCluster(clusterId);
        String password = cluster.getRedisPassword();
        param.setRedisPassword(password);
        List<Map<String, String>> nodeList = JedisUtil.getNodeList(param);
        for (Map<String, String> node : nodeList) {
            String ip = node.get("ip");
            int port = Integer.parseInt(node.get("port"));
            Jedis jedis = new Jedis(ip, port);
            if (StringUtils.isNotBlank(password)) {
                jedis.auth(password);
            }
            try {
                List<String> configList = jedis.configGet(configName);
                if (configList.size() != 2) {
                    break;
                }
                jedis.configSet(configName, configValue);
                jedis.clusterSaveConfig();
                if ("requirepass".equalsIgnoreCase(configName)) {
                    password = configValue.trim();
                    clusterDao.updatePassword(clusterId, password);
                }
                // 同步一下配置文件

                String configCmd = redisClient + " -c -h " + ip + " -p " + port + " -a " + password + " config rewrite";
                if (StringUtils.isNotBlank(password)) {
                    configCmd += " -a " + password + " config rewrite";
                } else {
                    configCmd += " config rewrite";
                }
                System.out.println(configCmd);
                RemoteShellUtil.localExec(configCmd);
            } catch (Exception e) {
                res = false;
                logger.error("", e);
            } finally {
                jedis.close();
            }
        }
        return res;
    }

    /*public boolean batchModfifyConfigFile(String myip, int myPort, String userName, String password, String fileFormat, String configName, String configValue) {
        if (StringUtils.isBlank(userName) || StringUtils.isBlank(fileFormat)) {
            return true;
        }
        List<Host> hostList = new ArrayList<>();
        List<Map<String, String>> nodeList = JedisUtil.getNodeList(myip, myPort);
        for (Map<String, String> node : nodeList) {
            String ip = node.get("ip");
            int port = Integer.parseInt(node.get("port"));
            Host host = new Host(ip, port);
            hostList.add(host);
        }
        return changeConfigFile(hostList, userName, password, fileFormat, configName, configValue);
    }*/


    public boolean initSlot(int clusterId, String address) {
        boolean res = true;
        Host host = NetUtil.getHost(address);
        String ip = host.getIp();
        int port = host.getPort();
        Cluster cluster = getCluster(clusterId);
        String password = cluster.getRedisPassword();
        ConnectionParam param = new ConnectionParam(ip, port, password);
        List<Map<String, String>> masterList = JedisUtil.getNodeList(param, true);
        int masterSize = masterList.size();
        List<SlotBalanceUtil.Shade> balanceSlots = SlotBalanceUtil.balanceSlot(masterSize);
        for (int i = 0; i < balanceSlots.size(); i++) {
            try {
                SlotBalanceUtil.Shade shade = balanceSlots.get(i);
                int start = shade.getStartSlot();
                int end = shade.getEndSlot();
                Map<String, String> hostMap = masterList.get(i);
                String itemIp = hostMap.get("ip");
                String itemPort = hostMap.get("port");
                int intItemPort = Integer.parseInt(itemPort);
                Jedis jedis = new Jedis(itemIp, intItemPort);
                if (StringUtils.isNotBlank(password)) {
                    jedis.auth(password);
                }
                try {
                    for (int slot = start; slot <= end; slot++) {
                        try {
                            String resstr = jedis.clusterAddSlots(slot);
                            if (!resstr.equals("OK")) {
                                jedis.clusterAddSlots(slot);
                            }
                        } catch (Exception e) {
                            logger.error("", e);
                            res = false;
                        }
                    }
                } catch (Exception e) {
                    res = false;
                    logger.error("", e);
                } finally {
                    jedis.close();
                }
            } catch (Exception e) {
                res = false;
                logger.error("", e);
            }
        }
        return res;
    }

    public boolean reShard(int clusterId, String ip, int port, int startKey, int endKey) {
        boolean res = false;
        Cluster cluster = getCluster(clusterId);
        String password = cluster.getRedisPassword();
        ConnectionParam param = new ConnectionParam(ip, port, password);
        Map<String, Map> masterNodes = JedisUtil.getMasterNodes(param);
        Jedis jedis = null;
        try {
            jedis = new Jedis(ip, port);
            if (StringUtils.isNotBlank(password)) {
                jedis.auth(password);
            }
            // 迁移必须要知道自己的 nodeid 和 source 的ip port nodeid
            for (int slot = startKey; slot <= endKey; slot++) {
                Map<String, String> slotObjmap = fillMoveSlotObject(masterNodes, ip, port, slot);
                String myselfId = slotObjmap.get("myselfId");
                String sourceId = slotObjmap.get("sourceId");
                String sourceIP = slotObjmap.get("sourceIP");
                String strSourcePort = slotObjmap.get("sourcePort");
                // 如果 strSourcePort 为空，则进行集群初始化
                if (StringUtils.isBlank(strSourcePort)) {
                    String resstr = jedis.clusterAddSlots(slot);
                    if (!resstr.equals("OK")) {
                        jedis.clusterAddSlots(slot);
                    }
                    continue;
                }
                int sourcePort = Integer.parseInt(strSourcePort);
                try {
                    moveSlot(myselfId, ip, port, password, sourceId, sourceIP, sourcePort, slot);
                } catch (Exception e) {
                    logger.error("", e);
                }
            }
            res = true;
        } catch (Exception e) {
            logger.error("move slot ", e);
        } finally {
            jedis.close();
        }
        return res;
    }

    /**
     * 移动 slot
     *
     * @param myselfId
     * @param ip
     * @param port
     * @param sourceId
     * @param sourceIP
     * @param sourcePort
     * @param slot
     */
    private void moveSlot(String myselfId, String ip, int port, String password, String sourceId, String sourceIP, int sourcePort, int slot) {
        Jedis myself = new Jedis(ip, port);
        Jedis source = new Jedis(sourceIP, sourcePort);
        if (StringUtils.isNotBlank(password)) {
            myself.auth(password);
            source.auth(password);
        }
        try {
            // 设置导入导出状态
            myself.clusterSetSlotImporting(slot, sourceId);
            source.clusterSetSlotMigrating(slot, myselfId);
            List<String> keys;
            // 真正迁移
            do {
                keys = source.clusterGetKeysInSlot(slot, 100);
                for (String key : keys) {
                    source.migrate(ip, port, key, 0, 600000);
                }
            } while (keys.size() > 0);
        } catch (Exception e) {
            logger.error("", e);
            // 出现异常就恢复 slot
            myself.clusterSetSlotStable(slot);
            source.clusterSetSlotStable(slot);
        } finally {
            // 设置 slot 给 myself 节点
            myself.clusterSetSlotNode(slot, myselfId);
            //？ 这个很奇怪如果没有设置 stable 它的迁移状态会一直在的
            source.clusterSetSlotStable(slot);
            source.close();
            myself.close();
        }
    }

    /**
     * 返回 slot
     *
     * @param
     * @param port
     */
    private Map<String, String> fillMoveSlotObject(Map<String, Map> masterNodes, String ip, int port, int slot) {
        Map<String, String> resultMap = new HashMap<>();
        String myselfId = "";
        String sourceIP = "";
        String sourcePort = "";
        String sourceId = "";
        for (Map.Entry<String, Map> masterNode : masterNodes.entrySet()) {
            Map<String, Object> master = masterNode.getValue();
            String masterHost = master.get("ip") + ":" + master.get("port");
            String nodeHost = ip + ":" + port;
            if (masterHost.equals(nodeHost)) {
                myselfId = (String) master.get("nodeId");
            }
            if (slotInMaster(master, slot)) {
                sourceId = (String) master.get("nodeId");
                sourceIP = (String) master.get("ip");
                sourcePort = String.valueOf(master.get("port"));
            }
            if (!"".equals(myselfId) && !"".equals(sourceIP) && "".equals(sourcePort) && !"".equals(sourceId)) {
                break;
            }
        }

        resultMap.put("myselfId", myselfId);
        resultMap.put("sourceIP", sourceIP);
        resultMap.put("sourcePort", sourcePort);
        resultMap.put("sourceId", sourceId);
        return resultMap;
    }

    private boolean slotInMaster(Map<String, Object> master, int slot) {
        String[] slots = (String[]) master.get("slot");
        for (Object itemSlot : slots) {
            String slotRange = (String) itemSlot;
            String[] slotArr = slotRange.split("-");
            if (2 == slotArr.length) {
                if (slot >= Integer.parseInt(slotArr[0]) && slot <= Integer.parseInt(slotArr[1])) {
                    return true;
                }
            } else if (1 == slotArr.length) {
                if (slot == Integer.parseInt(slotArr[0])) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean changeConfigFile(List<Host> hostList, String username, String password, String filePathFormat, String field, String value) {
        boolean res = true;
        for (Host host : hostList) {
            String ip = host.getIp();
            int port = host.getPort();
            String filePath = filePathFormat.replaceAll("\\{port\\}", String.valueOf(port));
            System.out.println(filePath);
            res = res & FileUtil.modifyFileContent(ip, port, username, password, filePath, field, value);
        }
        return res;
    }

    public boolean importDataToCluster(int clusterId, String address, String targetAddress, String keyFormat) {
        boolean res = false;
        try {
            redisManager.importDataToCluster(clusterId, address, targetAddress, keyFormat);
            res = true;
        } catch (Exception e) {
            logger.error("import to cluster", e);
        }
        return res;
    }

    public List<ClusterImportResult> getImportCountList() {
        return redisManager.getClusterImportResult();
    }

}