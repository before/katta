package net.sf.katta.protocol.operation.leader;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.sf.katta.master.DefaultDistributionPolicy;
import net.sf.katta.master.LeaderContext;
import net.sf.katta.master.Master;
import net.sf.katta.node.Node;
import net.sf.katta.protocol.InteractionProtocol;
import net.sf.katta.protocol.OperationQueue;
import net.sf.katta.protocol.metadata.NodeMetaData;
import net.sf.katta.protocol.operation.node.NodeOperation;
import net.sf.katta.protocol.operation.node.ShardDeployOperation;
import net.sf.katta.testutil.PrintMethodNames;
import net.sf.katta.testutil.TestResources;
import net.sf.katta.testutil.ZkTestSystem;

import org.junit.Rule;

public class MockedMasterNodeTest {

  @Rule
  public ZkTestSystem _zk = ZkTestSystem.getInstance();
  @Rule
  public PrintMethodNames _printMethodNames = new PrintMethodNames();

  protected InteractionProtocol _protocol = new InteractionProtocol(_zk.getZkClient(), _zk.getZkConf());
  protected LeaderContext _context = new LeaderContext(_protocol, new DefaultDistributionPolicy());

  private File _indexFile = TestResources.INDEX1;
  protected String _indexName = _indexFile.getName();
  protected String _indexPath = _indexFile.getAbsolutePath();
  protected int _shardCount = _indexFile.listFiles().length;

  private int _nodeCounter;
  private int _masterCounter;

  protected Master mockMaster() {
    Master master = mock(Master.class);
    when(master.getMasterName()).thenReturn("master" + _masterCounter++);
    return master;
  }

  protected OperationQueue<LeaderOperation> publishMaster() {
    Master master = mockMaster();
    return _protocol.publishMaster(master);
  }

  protected Node mockNode() {
    Node node = mock(Node.class);
    when(node.getName()).thenReturn("node" + _nodeCounter++);
    return node;
  }

  protected List<Node> mockNodes(int count) {
    List<Node> nodes = new ArrayList<Node>();
    for (int i = 0; i < count; i++) {
      nodes.add(mockNode());
    }
    return nodes;
  }

  protected OperationQueue<NodeOperation> publisNode(Node node) {
    return _protocol.publishNode(node, new NodeMetaData(node.getName()));
  }

  protected List<OperationQueue<NodeOperation>> publisNodes(List<Node> nodes) {
    List<OperationQueue<NodeOperation>> nodeQueues = new ArrayList<OperationQueue<NodeOperation>>();
    for (Node node : nodes) {
      nodeQueues.add(publisNode(node));
    }
    return nodeQueues;
  }

  protected void deployIndexWithError() throws Exception {
    IndexDeployOperation deployOperation = new IndexDeployOperation(_indexName, _indexPath, 3);
    deployOperation.execute(_context);
    deployOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
  }

  protected void deployIndex(List<Node> nodes, List<OperationQueue<NodeOperation>> nodeQueues) throws Exception {
    IndexDeployOperation deployOperation = new IndexDeployOperation(_indexName, _indexPath, 3);
    deployOperation.execute(_context);
    publisShards(nodes, nodeQueues);
    deployOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
  }

  protected void publisShards(List<Node> nodes, List<OperationQueue<NodeOperation>> nodeQueues)
          throws InterruptedException {
    for (int i = 0; i < nodes.size(); i++) {
      publisShard(nodes.get(i), nodeQueues.get(i));
    }
  }

  protected void publisShard(Node node, OperationQueue<NodeOperation> nodeQueue) throws InterruptedException {
    Set<String> shardNames = ((ShardDeployOperation) nodeQueue.remove()).getShardNames();
    for (String shardName : shardNames) {
      _protocol.publishShard(node, shardName, new HashMap<String, String>());
    }
  }

}