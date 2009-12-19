package net.sf.katta.integrationTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Set;

import net.sf.katta.integrationTest.support.KattaMiniCluster;
import net.sf.katta.protocol.InteractionProtocol;
import net.sf.katta.protocol.metadata.IndexMetaData;
import net.sf.katta.protocol.metadata.IndexDeployError.ErrorType;
import net.sf.katta.protocol.metadata.IndexMetaData.Shard;
import net.sf.katta.protocol.operation.leader.IndexDeployOperation;
import net.sf.katta.protocol.operation.leader.IndexUndeployOperation;
import net.sf.katta.testutil.TestResources;
import net.sf.katta.testutil.TestUtil;
import net.sf.katta.util.FileUtil;
import net.sf.katta.util.NodeConfiguration;
import net.sf.katta.util.ZkConfiguration;

import org.junit.After;
import org.junit.Test;

public class MasterIntegrationTest {

  // TODO let the cluster run, but cleanup indices ?
  private KattaMiniCluster _miniCluster;
  private final static File INDEX_FILE = TestResources.INDEX1;
  private final static String INDEX_NAME = TestResources.INDEX1.getName() + 0;
  private final static int SHARD_COUNT = INDEX_FILE.list(FileUtil.VISIBLE_FILES_FILTER).length;

  @After
  public void tearDown() throws Exception {
    if (_miniCluster != null) {
      _miniCluster.stop();
    }
  }

  private KattaMiniCluster startMiniCluster(int nodeCount, int indexCount, int replicationCount) throws Exception {
    ZkConfiguration conf = new ZkConfiguration();
    FileUtil.deleteFolder(new File(conf.getZKDataDir()));
    FileUtil.deleteFolder(new File(conf.getZKDataLogDir()));
    FileUtil.deleteFolder(new NodeConfiguration().getShardFolder());

    // start katta cluster
    KattaMiniCluster miniCluster = new KattaMiniCluster(conf, nodeCount);
    miniCluster.start();

    miniCluster.deployTestIndexes(INDEX_FILE, indexCount, replicationCount);
    return miniCluster;
  }

  private int countShardDeployments(InteractionProtocol protocol, String indexName) {
    IndexMetaData indexMD = protocol.getIndexMD(indexName);
    int shardDeployCount = 0;
    for (Shard shard : indexMD.getShards()) {
      shardDeployCount += protocol.getShardNodes(shard.getName()).size();
    }
    return shardDeployCount;
  }

  @Test(timeout = 20000)
  public void testDeployAndUndeployIndex() throws Exception {
    int nodeCount = 2;
    _miniCluster = startMiniCluster(nodeCount, 0, nodeCount);
    final InteractionProtocol protocol = _miniCluster.getProtocol();

    IndexDeployOperation deployOperation = new IndexDeployOperation(INDEX_NAME, "file://"
            + INDEX_FILE.getAbsolutePath(), nodeCount);
    protocol.addLeaderOperation(deployOperation);

    TestUtil.waitUntilIndexDeployed(protocol, INDEX_NAME);
    assertEquals(1, protocol.getIndices().size());
    IndexMetaData indexMD = protocol.getIndexMD(INDEX_NAME);
    assertEquals(null, indexMD.getDeployError());
    assertEquals(SHARD_COUNT, indexMD.getShards().size());

    Set<Shard> shards = indexMD.getShards();
    for (Shard shard : shards) {
      assertEquals(nodeCount, protocol.getShardNodes(shard.getName()).size());
    }

    // undeploy
    IndexUndeployOperation undeployOperation = new IndexUndeployOperation(INDEX_NAME);
    protocol.addLeaderOperation(undeployOperation);
    TestUtil.waitUntilIndexUndeployed(protocol, indexMD);

    assertEquals(0, protocol.getIndices().size());
    assertEquals(null, protocol.getIndexMD(INDEX_NAME));
    for (Shard shard : shards) {
      assertEquals(0, protocol.getShardNodes(shard.getName()).size());
    }
  }

  @Test(timeout = 20000)
  public void testDeployError() throws Exception {
    int nodeCount = 2;
    _miniCluster = startMiniCluster(nodeCount, 0, nodeCount);
    final InteractionProtocol protocol = _miniCluster.getProtocol();

    final File indexFile = TestResources.INVALID_INDEX;
    IndexDeployOperation deployOperation = new IndexDeployOperation(INDEX_NAME,
            "file://" + indexFile.getAbsolutePath(), nodeCount);
    protocol.addLeaderOperation(deployOperation);
    TestUtil.waitUntilIndexDeployed(protocol, INDEX_NAME);
    assertEquals(1, protocol.getIndices().size());
    IndexMetaData indexMD = protocol.getIndexMD(INDEX_NAME);
    assertNotNull(indexMD.getDeployError());
    assertEquals(ErrorType.SHARDS_NOT_DEPLOYABLE, indexMD.getDeployError().getErrorType());
  }

  @Test(timeout = 20000)
  public void testRebalanceIndexAfterNodeCrash() throws Exception {
    int nodeCount = 3;
    int replicationCount = nodeCount - 1;
    _miniCluster = startMiniCluster(nodeCount, 1, replicationCount);
    final InteractionProtocol protocol = _miniCluster.getProtocol();
    assertEquals(1, protocol.getIndices().size());

    int optimumShardDeployCount = SHARD_COUNT * replicationCount;
    assertEquals(optimumShardDeployCount, countShardDeployments(protocol, INDEX_NAME));

    _miniCluster.getNode(0).shutdown();
    assertTrue(optimumShardDeployCount > countShardDeployments(protocol, INDEX_NAME));

    Thread.sleep(2000);
    assertEquals(optimumShardDeployCount, countShardDeployments(protocol, INDEX_NAME));
  }

  @Test(timeout = 20000)
  public void testIndexPickupAfterMasterRestart() throws Exception {
    _miniCluster = startMiniCluster(3, 1, 3);
    final InteractionProtocol protocol = _miniCluster.getProtocol();
    assertEquals(1, protocol.getIndices().size());

    _miniCluster.restartMaster();
    assertEquals(1, protocol.getIndices().size());
    // TODO protocol get ReplictaionReport
  }

  @Test
  public void testReplicateUnderreplicatedIndexesAfterNodeAdding() throws Exception {
    int nodeCount = 2;
    int replicationCount = nodeCount + 1;
    _miniCluster = startMiniCluster(nodeCount, 1, replicationCount);
    final InteractionProtocol protocol = _miniCluster.getProtocol();
    assertEquals(1, protocol.getIndices().size());

    int optimumShardDeployCount = SHARD_COUNT * replicationCount;
    assertTrue(optimumShardDeployCount > countShardDeployments(protocol, INDEX_NAME));

    _miniCluster.startAdditionalNode();
    Thread.sleep(2000);
    assertTrue(optimumShardDeployCount == countShardDeployments(protocol, INDEX_NAME));
  }
}
