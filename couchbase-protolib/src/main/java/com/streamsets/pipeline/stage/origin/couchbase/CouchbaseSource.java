/*
 * Copyright 2019 StreamSets.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.couchbase;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.ControlEventHandler;
import com.couchbase.client.dcp.DataEventHandler;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.message.DcpDeletionMessage;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.DcpSnapshotMarkerRequest;
import com.couchbase.client.dcp.transport.netty.ChannelFlowController;
import com.couchbase.client.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.deps.io.netty.util.CharsetUtil;
import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseSource;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.lib.couchbase.CouchbaseDCPConnector;
import com.streamsets.pipeline.lib.couchbase.Errors;
import com.streamsets.pipeline.lib.parser.DataParserFactory;
import com.streamsets.pipeline.stage.destination.couchbase.CouchbaseTarget;
import com.streamsets.pipeline.stage.origin.lib.DataParserFormatConfig;
import com.streamsets.pipeline.lib.parser.DataParser;
import com.streamsets.pipeline.lib.parser.DataParserException;
import com.streamsets.pipeline.lib.parser.DataParserFactory;
import com.streamsets.pipeline.lib.util.ThreadUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.java.document.JsonDocument;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.stage.common.DefaultErrorRecordHandler;

public class CouchbaseSource extends BaseSource {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseTarget.class);

    private final CouchbaseSourceConfig config;

    private CouchbaseDCPConnector dcpConnector;
    private Client client;

    protected DataParserFactory parserFactory;
    private DataParserFormatConfig dataFormatConfig;
    
    private DefaultErrorRecordHandler errorRecordHandler;
    
    private boolean connected = false;
    
    private AtomicInteger numberRecordsProcessed;

    CouchbaseSource(CouchbaseSourceConfig config) {
        this.config = config;
    }

    @Override
    protected List<ConfigIssue> init() {
        // Validate configuration values and open any required resources.
        List<ConfigIssue> issues = super.init();
        errorRecordHandler = new DefaultErrorRecordHandler(getContext());

        //Connect to Couchbase DB
        LOG.info("Connecting to Couchbase");

        //Connect to the Couchbase Server
        try {
            dcpConnector = CouchbaseDCPConnector.getInstance(config, issues, getContext());
            client = dcpConnector.getClient();
        } catch (Exception e) {
            issues.add(getContext().createConfigIssue(Groups.COUCHBASE.name(), "config.connection",
                    Errors.COUCHBASE_02,
                    e.toString(),
                    e));
        }

        //Configure DataParser
        dataFormatConfig = new DataParserFormatConfig();
        dataFormatConfig.init(getContext(),
                DataFormat.JSON,
                Groups.COUCHBASE.name(),
                "dataFormatConfig.",
                issues);
        parserFactory = dataFormatConfig.getParserFactory();
        
        numberRecordsProcessed = new AtomicInteger(0);

        return issues;
    }

    @Override
    public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
        // Offsets can vary depending on the data source. Here we use an integer as an example only.
        long nextSourceOffset = 0;
        lastSourceOffset = lastSourceOffset == null ? "" : lastSourceOffset;
        if (!lastSourceOffset.equals("")) {
          nextSourceOffset = Long.parseLong(lastSourceOffset);
        }

        int recordCounter = 0;
        long startTime = System.currentTimeMillis();
        int maxRecords = Math.min(maxBatchSize, config.batchSize);
        
        //Start connection to Couchbase DCP queue and pass the starting point
        if (!connected)
            connected = dcpConnector.connect(config.startingPoint);

        while (recordCounter < maxRecords && (startTime + config.maxBatchWaitTime) > System.currentTimeMillis()) {
          JsonDocument doc = dcpConnector.getBuffer().poll();
          if (null == doc) {
            try {
              Thread.sleep(100);
            } catch (Exception e) {
              LOG.debug(e.getMessage(), e);
              break;
            }
          } else {
            List<Record> records = processJSONDocument(doc);
            for (Record record : records) {
              batchMaker.addRecord(record);
            }
            recordCounter += records.size();
            ++nextSourceOffset;
          }
          
          
        }
        return lastSourceOffset;
    }
        
    /**
     *
     * @param doc
     * @return
     * @throws StageException
     */
    protected List<Record> processJSONDocument(JsonDocument doc) throws StageException {
        List<Record> records = new ArrayList<>();
        
        try (DataParser parser = parserFactory.getParser(doc.id(), doc.content().toString())) {
          Record record = parser.parse();
          //Add Document Key of JSON Document as "doc_id" attribute
          //Consistent with Couchbase Destionation Document Key setting
          record.getHeader().setAttribute("doc_id", doc.id());
          while (record != null) {
            records.add(record);
            record = parser.parse();
          }
        } catch (Exception ex) {
          LOG.error(Errors.COUCHBASE_19.getMessage(), ex);
        } 
        
        return records;
    }
    
    @Override
    public void destroy() {
        // Clean up any open resources.
        client.disconnect().await();
        dcpConnector = null;
        super.destroy();
    }
    
}