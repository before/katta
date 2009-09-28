/**
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.katta.master;

import junit.framework.Assert;
import net.sf.katta.AbstractKattaTest;
import net.sf.katta.client.DeployClient;
import net.sf.katta.client.IDeployClient;
import net.sf.katta.client.IIndexDeployFuture;
import net.sf.katta.client.LuceneClient;
import net.sf.katta.index.IndexMetaData.IndexState;
import net.sf.katta.node.LuceneServer;
import net.sf.katta.node.Node;
import net.sf.katta.node.Query;
import net.sf.katta.testutil.TestResources;
import net.sf.katta.util.KattaException;
import net.sf.katta.util.NodeConfiguration;
import net.sf.katta.util.ZkConfiguration;
import net.sf.katta.util.ZkKattaUtil;

import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.proto.WatcherEvent;

@SuppressWarnings("deprecation")
public class FailTest extends AbstractKattaTest {

  public void testMasterFail() throws Exception {
    final ZkClient masterClient = ZkKattaUtil.startZkClient(_conf, 30000);
    final ZkClient secMasterClient = ZkKattaUtil.startZkClient(_conf, 30000);

    final Master master = new Master(_conf, masterClient, true);
    master.start();

    // start secondary master..
    final Master secMaster = new Master(_conf, secMasterClient, true);
    secMaster.start();

    waitForPath(masterClient, _conf.getZKMasterPath());

    masterClient.readData(_conf.getZKMasterPath());

    // kill master
    master.shutdown();
    // just make sure we can read the file
    waitForPath(secMasterClient, _conf.getZKMasterPath());
    assertTrue(secMaster.isMaster());

    secMaster.shutdown();
  }

  // TODO test zk disconnect

  public void testNodeFailure() throws Exception {
    final MasterStartThread masterThread = startMaster();
    final ZkClient zkClientMaster = masterThread.getZkClient();

    // create 3 nodes
    final NodeConfiguration sconf1 = new NodeConfiguration();
    final String defaulFolder = sconf1.getShardFolder().getAbsolutePath();
    sconf1.setShardFolder(defaulFolder + "/" + 1);
    final DummyNode node1 = new DummyNode(_conf, sconf1);

    final NodeConfiguration sconf2 = new NodeConfiguration();
    final String defaulFolder2 = sconf2.getShardFolder().getAbsolutePath();
    sconf2.setShardFolder(defaulFolder2 + "/" + 2);
    final DummyNode node2 = new DummyNode(_conf, sconf2);

    final NodeConfiguration sconf3 = new NodeConfiguration();
    final String defaulFolder3 = sconf3.getShardFolder().getAbsolutePath();
    sconf3.setShardFolder(defaulFolder3 + "/" + 3);
    final DummyNode node3 = new DummyNode(_conf, sconf3);
    waitForChilds(zkClientMaster, _conf.getZKNodesPath(), 3);
    masterThread.join();
    waitForPath(zkClientMaster, _conf.getZKMasterPath());

    // deploy index
    final IDeployClient deployClient = new DeployClient(zkClientMaster, _conf);
    final String indexName = "index";
    deployClient.addIndex(indexName, TestResources.UNZIPPED_INDEX.getAbsolutePath(), 3).joinDeployment();
    final LuceneClient client = new LuceneClient(_conf);
    assertEquals(2, client.count(new Query("foo:bar"), new String[] { indexName }));
    assertEquals(1, node1.countShards());
    assertEquals(1, node2.countShards());
    assertEquals(1, node3.countShards());
    node1.close();
    assertEquals(2, client.count(new Query("foo:bar"), new String[] { indexName }));
    node2.close();
    assertEquals(2, client.count(new Query("foo:bar"), new String[] { indexName }));

    // add count Shards to Node Object... and check why no reasignment
    // happens....

    // kill 2 nodes

    // we should be still be able to search

    // bring back 2 nodes

    // things should be good distributed again.
    client.close();
    node3.close();
    masterThread.shutdown();
  }

  //TODO PVo enable later
  public void _testZkReconnectDuringDeployment() throws InterruptedException, KattaException {
    final MasterStartThread masterThread = startMaster();
    final ZkClient masterZkClient = masterThread.getZkClient();

    final NodeConfiguration sconf1 = new NodeConfiguration();
    final String defaulFolder = sconf1.getShardFolder().getAbsolutePath();
    sconf1.setShardFolder(defaulFolder + "/" + 1);
    /* final DummyNode node1 = */new DummyNode(_conf, sconf1);

    final IDeployClient deployClient = new DeployClient(masterZkClient, _conf);

    WatchedEvent event = new WatchedEvent(new WatcherEvent(EventType.None.getIntValue(), KeeperState.Expired
            .getIntValue(), null));
    for (int i = 0; i < 100; i++) {
      final String indexName = "index" + i;
      IIndexDeployFuture index = deployClient.addIndex(indexName, TestResources.UNZIPPED_INDEX.getAbsolutePath(), 3);

      System.out.println("deploying: " + indexName);
      masterZkClient.getEventLock().lock();
      masterZkClient.process(event);
      masterZkClient.getEventLock().unlock();
      index.joinDeployment();
      Assert.assertTrue(index.getState().equals(IndexState.DEPLOYED));
      deployClient.removeIndex(indexName);
      System.out.println("removing: " + indexName);
    }
  }


  private static class DummyNode {

    private final Node _node;

    public DummyNode(final ZkConfiguration conf, final NodeConfiguration nodeConfiguration) {
      _node = new Node(conf, _zkServer.getZkClient(), nodeConfiguration, new LuceneServer());
      _node.start();
    }

    public int countShards() {
      return _node.getDeployedShards().size();
    }

    void close() {
      _node.shutdown();
    }
  }
}
