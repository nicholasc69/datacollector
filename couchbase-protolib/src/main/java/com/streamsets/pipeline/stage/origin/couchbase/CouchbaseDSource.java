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

import com.streamsets.pipeline.api.ConfigDefBean;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.base.configurablestage.DSource;

@StageDef(
    version = 1,
    label = "Couchbase",
    description = "Consume data from Couchbase",
    icon = "couchbase.png",
    recordsByRef = true,
    onlineHelpRefUrl = "index.html?contextID=task_cnl_dwq_h2b"
)
@ConfigGroups(value = com.streamsets.pipeline.stage.origin.couchbase.Groups.class)
@GenerateResourceBundle
public class CouchbaseDSource extends DSource {

    @ConfigDefBean
    public CouchbaseSourceConfig config;


    @Override
    protected Source createSource() {
        return new CouchbaseSource(config);
    }
    
}
