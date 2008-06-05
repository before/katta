/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.katta.util;

public class NodeConfiguration extends KattaConfiguration {

  private final static String NODE_SERVER_PORT_START = "node.server.port.start";
  private static final String SHARD_FOLDER = "node.shard.folder";

  public NodeConfiguration() {
    super("/katta.node.properties");
  }

  public int getStartPort() {
    return getInt(NODE_SERVER_PORT_START);
  }

  public String getShardFolder() {
    return _properties.getProperty(SHARD_FOLDER);
  }

  public void setShardFolder(final String value) {
    _properties.setProperty(SHARD_FOLDER, value);
  }
}