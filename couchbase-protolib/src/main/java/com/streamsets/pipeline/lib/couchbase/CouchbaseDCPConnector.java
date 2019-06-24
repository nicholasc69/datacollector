/*
 * Copyright 2018 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.lib.couchbase;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.ControlEventHandler;
import com.couchbase.client.dcp.DataEventHandler;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.DcpSnapshotMarkerRequest;
import com.couchbase.client.dcp.transport.netty.ChannelFlowController;
import com.couchbase.client.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.deps.io.netty.util.CharsetUtil;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.lib.parser.DataParser;
import com.streamsets.pipeline.stage.origin.couchbase.Groups;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

/**
 * CouchbaseConnector provides a singleton per pipeline for all connection objects to a Couchbase DCP Session.
 */
public class CouchbaseDCPConnector {
  private final Client client;
  private volatile boolean isClosed;

  private static final String INSTANCE = "couchbase_dcp_client";

  private static final Logger LOG = LoggerFactory.getLogger(CouchbaseDCPConnector.class);
  private BaseCouchbaseConfig config;
  private ConcurrentLinkedQueue<JsonDocument> buffer = new ConcurrentLinkedQueue<>();
          

  /**
   * Instantiates Couchbase connection objects and returns any encountered issues
   *
   * @param config the stage configuration
   * @param issues the list of configuration issues
   * @param context the context for the stage
   */
  private CouchbaseDCPConnector(BaseCouchbaseConfig config, List<Stage.ConfigIssue> issues, Stage.Context context) {
      this.config = config;
    Client.Builder builder = Client.configure();

    

    if(config.couchbase.connectTimeout > 0) {
      LOG.debug("Setting SDK Environment parameter connectTimeout to {}", config.couchbase.connectTimeout);
      builder.connectTimeout(config.couchbase.connectTimeout);
    }


    if(config.couchbase.tls.tlsEnabled) {
      LOG.debug("Enabling TLS");
      builder.sslEnabled(true);

      LOG.debug("Using keystore: {}", config.couchbase.tls.keyStoreFilePath);
      builder.sslKeystore(config.couchbase.tls.getKeyStore());

    }

    LOG.debug("Creating CouchbaseEnvironment");

    LOG.debug("Connecting to cluster with nodes {}", config.couchbase.nodes);
    builder.hostnames(config.couchbase.nodes);

    if(config.credentials.version == AuthenticationType.USER) {
      try {
        LOG.debug("Using user authentication");
        builder.username(config.credentials.userName.get());
        builder.password(config.credentials.userPassword.get());
        builder.bucket(config.couchbase.bucket);
      } catch (Exception e) {
        issues.add(context.createConfigIssue(Groups.CREDENTIALS.name(), "config.userPassword", Errors.COUCHBASE_03, e.toString(), e));
      }
    }

    try {
      if (config.credentials.version == AuthenticationType.BUCKET) {
          LOG.debug("Using bucket authentication");
          builder.bucket(config.couchbase.bucket);
          builder.password(config.credentials.bucketPassword.get());
        
      } 
    } catch (Exception e) {
      issues.add(context.createConfigIssue(Groups.COUCHBASE.name(), "config.nodes", Errors.COUCHBASE_02, e.toString(), e));
    }
    
    //Now connect to Couchbase with the client using the builder
    client = builder.build();
    
    //Add Control and Data Handlers to client
    client.controlEventHandler(new ControlHandler());
    client.dataEventHandler(new DataHandler());
    
  }

  /**
   * Maintains a singleton instance of the CouchbaseConnector object per pipeline
   *
   * @param config the stage configuration
   * @param issues the list of configuration issues
   * @param context the context for the stage
   * @return a singleton instance for Couchbase connections
   */
  public static synchronized CouchbaseDCPConnector getInstance(BaseCouchbaseConfig config, List<Stage.ConfigIssue> issues, Stage.Context context) {

    Map<String, Object> runnerSharedMap = context.getStageRunnerSharedMap();

    if(runnerSharedMap.containsKey(INSTANCE)) {
      LOG.debug("Using existing instance of CouchbaseConnector");
    } else {
      LOG.debug("CouchbaseConnector not yet instantiated. Creating new instance");
      validateConfig(config, issues, context);

      if(issues.isEmpty()) {
        runnerSharedMap.put(INSTANCE, new CouchbaseDCPConnector(config, issues, context));
      }
    }

    return (CouchbaseDCPConnector) runnerSharedMap.get(INSTANCE);
  }

   /**
   * Return Client instance
   */
  
  
  public Client getClient() {
      return client;
  }
  
  public boolean connect(StartingPointDataType startingPoint) {

        //Connecting...
        // Connect the sockets
        client.connect().await();

        // Initialize the state (either start from beginning or now, never stop)
        if (startingPoint.equals(StartingPointDataType.BEGINNING))
            client.initializeState(StreamFrom.BEGINNING, StreamTo.INFINITY).await();
        else
            client.initializeState(StreamFrom.NOW, StreamTo.INFINITY).await();
        
        // Start streaming on all partitions
        client.startStreaming().await();

        return true;
    }

  /**
   * Disconnects from Couchbase and releases all resources
   */
  public synchronized void close() {

    if(!isClosed) {
      LOG.debug("Shutting down Couchbase environment");
      // Proper Shutdown
      client.disconnect().await();
    }
    isClosed = true;    
  }

  /**
   * Validates connection configurations that don't require runtime exception handling
   * @param config the stage configuration
   * @param issues the list of configuration issues
   * @param context the context for the stage
   */
  private static void validateConfig(BaseCouchbaseConfig config, List<Stage.ConfigIssue> issues, Stage.Context context){
    if(config.couchbase.nodes == null) {
      issues.add(context.createConfigIssue(Groups.COUCHBASE.name(), "config.couchbase.nodes", Errors.COUCHBASE_29));
    }

    if(config.couchbase.kvTimeout < 0) {
      issues.add(context.createConfigIssue(Groups.COUCHBASE.name(), "config.couchbase.kvTimeout", Errors.COUCHBASE_30));
    }

    if(config.couchbase.connectTimeout < 0) {
      issues.add(context.createConfigIssue(Groups.COUCHBASE.name(), "config.couchbase.connectTimeout", Errors.COUCHBASE_31));
    }

    if(config.couchbase.disconnectTimeout < 0) {
      issues.add(context.createConfigIssue(Groups.COUCHBASE.name(), "config.couchbase.disconnectTimeout", Errors.COUCHBASE_32));
    }

    if(config.couchbase.tls.tlsEnabled) {
      config.couchbase.tls.init(context, Groups.COUCHBASE.name(), "config.couchbase.tls.", issues);
    }

    if(config.credentials.version == null) {
      issues.add(context.createConfigIssue(Groups.CREDENTIALS.name(), "config.credentials.version", Errors.COUCHBASE_33));
    }

    if(config.credentials.version == AuthenticationType.USER) {
      if(config.credentials.userName == null) {
        issues.add(context.createConfigIssue(Groups.CREDENTIALS.name(), "config.credentials.userName", Errors.COUCHBASE_34));
      }

      if(config.credentials.userPassword == null) {
        issues.add(context.createConfigIssue(Groups.CREDENTIALS.name(), "config.credentials.userPassword", Errors.COUCHBASE_35));
      }
    }
  }
  
  public ConcurrentLinkedQueue<JsonDocument> getBuffer() {
      return buffer;
  }
 
  
    class ControlHandler implements ControlEventHandler {

      @Override
      public void onEvent(ChannelFlowController flowController, ByteBuf event) {
                  if (DcpSnapshotMarkerRequest.is(event)) {
                      flowController.ack(event);
                  }
                  event.release();
              }

  }

    class DataHandler implements DataEventHandler {

        @Override
        public void onEvent(ChannelFlowController flowController, ByteBuf event) {
            String key = DcpMutationMessage.keyString(event);
            String body = DcpMutationMessage.content(event).toString(CharsetUtil.UTF_8);

            JsonObject jsonObj = JsonObject.fromJson(body);
            JsonDocument jsonDoc = JsonDocument.create(key, jsonObj);

            buffer.add(jsonDoc);
        }

    }
}

