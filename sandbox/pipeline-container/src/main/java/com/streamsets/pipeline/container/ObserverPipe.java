/**
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
package com.streamsets.pipeline.container;

import com.google.common.base.Preconditions;
import com.streamsets.pipeline.api.Module;
import com.streamsets.pipeline.config.Configuration;

class ObserverPipe extends Pipe {

  private Observer observer;

  private static ModuleInfo createObserverInfo(Module.Info info) {
    return new ModuleInfo("Observer", "-", "Pipeline built-in observer", info.getInstanceName() + ":observer", false);
  }

  private ObserverPipe(Pipe pipe, Observer observer) {
    super(pipe.getPipelineInfo(), pipe.getMetrics(), createObserverInfo(pipe.getModuleInfo()),
          pipe.getOutputLanes(), pipe.getOutputLanes());
    Preconditions.checkNotNull(observer, "observer cannot be null");
    this.observer = observer;
  }

  public ObserverPipe(SourcePipe pipe, Observer observer) {
    this((Pipe)pipe, observer);
  }

  public ObserverPipe(ProcessorPipe pipe, Observer observer) {
    this((Pipe)pipe, observer);
  }

  @Override
  public void init() {
    observer.init();
  }

  @Override
  public void destroy() {
    observer.destroy();
  }

  @Override
  public void configure(Configuration conf) {
    Preconditions.checkNotNull(conf, "conf cannot be null");
    observer.configure(conf);
  }

  @Override
  protected void processBatch(PipeBatch batch) {
    Preconditions.checkNotNull(batch, "batch cannot be null");
    if (observer.isActive()) {
      observer.observe(batch);
    }
  }

}
