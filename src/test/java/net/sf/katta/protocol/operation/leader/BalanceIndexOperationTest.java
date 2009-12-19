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
package net.sf.katta.protocol.operation.leader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import net.sf.katta.node.Node;
import net.sf.katta.protocol.OperationQueue;
import net.sf.katta.protocol.metadata.IndexMetaData;
import net.sf.katta.protocol.operation.node.NodeOperation;

import org.junit.Test;

public class BalanceIndexOperationTest extends MockedMasterNodeTest {

  @Test
  public void testLocksOperation() throws Exception {
    // only lock operations on same index
    LeaderOperation op1 = new BalanceIndexOperation("index1");
    LeaderOperation op2 = new BalanceIndexOperation("index1");
    LeaderOperation op3 = new BalanceIndexOperation("index2");
    LeaderOperation otherOp = mock(LeaderOperation.class);

    assertTrue(op1.locksOperation(op1));
    assertTrue(op1.locksOperation(op2));
    assertTrue(op2.locksOperation(op1));
    assertFalse(op1.locksOperation(op3));
    assertFalse(op1.locksOperation(otherOp));
    assertFalse(op3.locksOperation(op1));
    assertFalse(otherOp.locksOperation(op1));
  }

  @Test
  public void testBalanceUnderreplicatedIndex() throws Exception {
    // add nodes and index
    List<Node> nodes = mockNodes(2);
    List<OperationQueue<NodeOperation>> nodeQueues = publisNodes(nodes);
    deployIndexWithError();

    // index deployed on 2 nodes / desired replica is 3
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(1, nodeqQueue.size());
    }
    publisShards(nodes, nodeQueues);

    // balance the index does not change anything
    BalanceIndexOperation balanceOperation = new BalanceIndexOperation(_indexName);
    balanceOperation.execute(_context);
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(0, nodeqQueue.size());
    }

    // add node and then balance again
    Node node3 = mockNode();
    OperationQueue<NodeOperation> nodeQueue3 = publisNode(node3);
    assertEquals(0, nodeQueue3.size());

    balanceOperation.execute(_context);
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(0, nodeqQueue.size());
    }
    assertEquals(1, nodeQueue3.size());
  }

  @Test
  public void testBalanceOverreplicatedIndex() throws Exception {
    // add nodes and index
    List<Node> nodes = mockNodes(3);
    List<OperationQueue<NodeOperation>> nodeQueues = publisNodes(nodes);
    deployIndexWithError();
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(1, nodeqQueue.size());
    }

    // publish shards
    publisShards(nodes, nodeQueues);

    // balance the index does not change anything
    BalanceIndexOperation balanceOperation = new BalanceIndexOperation(_indexName);
    balanceOperation.execute(_context);
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(0, nodeqQueue.size());
    }

    // decrease the replication count and then balance again
    IndexMetaData indexMD = _protocol.getIndexMD(_indexName);
    indexMD.setReplicationLevel(2);
    _protocol.updateIndexMD(indexMD);
    balanceOperation.execute(_context);
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(1, nodeqQueue.size());
    }
  }

  @Test
  public void testUnbalancedIndexAfterBalancingIndex() throws Exception {
    // add nodes and index
    List<Node> nodes = mockNodes(2);
    List<OperationQueue<NodeOperation>> nodeQueues = publisNodes(nodes);
    deployIndexWithError();

    // index deployed on 2 nodes / desired replica is 3
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(1, nodeqQueue.size());
    }
    publisShards(nodes, nodeQueues);

    // balance the index does not change anything
    BalanceIndexOperation balanceOperation = new BalanceIndexOperation(_indexName);
    balanceOperation.execute(_context);
    for (OperationQueue<NodeOperation> nodeqQueue : nodeQueues) {
      assertEquals(0, nodeqQueue.size());
    }

    // node completion does not add another balance op since not enough nodes
    // are there
    OperationQueue<LeaderOperation> masterQueue = _protocol.publishMaster(mockMaster());
    assertEquals(0, masterQueue.size());
    balanceOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
    assertEquals(0, masterQueue.size());

    // add node and now the balance op should add itself for retry
    Node node3 = mockNode();
    OperationQueue<NodeOperation> nodeQueue3 = publisNode(node3);
    balanceOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
    assertEquals(1, masterQueue.size());

    // now do the balance
    assertEquals(0, nodeQueue3.size());
    balanceOperation.execute(_context);
    assertEquals(1, nodeQueue3.size());
    publisShard(node3, nodeQueue3);

    // now it shouldn't add itself again since the index is balanced
    balanceOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
    assertEquals(1, masterQueue.size());
  }

  @Test
  public void testBalanceErrorIndex() throws Exception {
    // add nodes and index
    List<Node> nodes = mockNodes(2);
    List<OperationQueue<NodeOperation>> nodeQueues = publisNodes(nodes);
    deployIndexWithError();
    assertNotNull(_protocol.getIndexMD(_indexName).getDeployError());

    // balance the index should remove the error
    publisShards(nodes, nodeQueues);
    BalanceIndexOperation balanceOperation = new BalanceIndexOperation(_indexName);
    balanceOperation.execute(_context);
    balanceOperation.nodeOperationsComplete(_context, Collections.EMPTY_LIST);
    assertNull(_protocol.getIndexMD(_indexName).getDeployError());
  }

}