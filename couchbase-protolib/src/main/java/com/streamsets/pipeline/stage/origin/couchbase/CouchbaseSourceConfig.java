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
package com.streamsets.pipeline.stage.origin.couchbase;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.lib.couchbase.AuthenticationType;
import com.streamsets.pipeline.lib.couchbase.AuthenticationTypeChooserValues;
import com.streamsets.pipeline.lib.couchbase.BaseCouchbaseConfig;
import com.streamsets.pipeline.lib.couchbase.StartingPointDataType;
import com.streamsets.pipeline.lib.couchbase.StartingPointDataTypeChooserValues;
import com.streamsets.pipeline.lib.el.TimeEL;

public class CouchbaseSourceConfig extends BaseCouchbaseConfig{

  /**
   * Flow Control tab
   */
  @ConfigDef(
    required = false,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "1000",
    label = "Buffer Size (Bytes)",
    displayPosition = 10,
    description = "Buffer Size in Bytes",
    group = "FLOWCONTROL"
  )
  public int bufferSzie;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "75",
      label = "Buffer Acknowledgement Water Mark %",
      displayPosition = 20,
      description = "The Water Mark Percentage of the buffer size when client "
              + "acknowledges againts serever",
      group = "FLOWCONTROL"
  )
  public int bufferAckWatermark;
  
  @ConfigDef(
      type = ConfigDef.Type.NUMBER,
      label = "Batch Size (records)",
      defaultValue = "1000",
      required = true,
      min = 2, // Batch size of 1 in MongoDB is special and analogous to LIMIT 1
      displayPosition = 30,
      group = "FLOWCONTROL"
  )
  public int batchSize;
  
  @ConfigDef(
      type = ConfigDef.Type.NUMBER,
      label = "Max Batch Wait Time",
      defaultValue = "${5 * SECONDS}",
      required = true,
      elDefs = {TimeEL.class},
      evaluation = ConfigDef.Evaluation.IMPLICIT,
      displayPosition = 40,
      group = "FLOWCONTROL"
  )
  public long maxBatchWaitTime;
  
@ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "BEGINNING",
      label = "Starting Point",
      description = "Use BEGINNING to proccess all existing documents and new mutations. \n"
              + "Use NOW to start processing new mutations",
      displayPosition = 50,
      group = "FLOWCONTROL"
  )
  @ValueChooserModel(StartingPointDataTypeChooserValues.class)
  public StartingPointDataType startingPoint = StartingPointDataType.BEGINNING;

}
