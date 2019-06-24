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

import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.Label;

@GenerateResourceBundle
public enum StartingPointDataType implements Label {
  BEGINNING("Beginning", String.class),
  NOW("Now", String.class);

  private final String label;
  private final Class className;

  StartingPointDataType(String label, Class className) {
    this.label = label;
    this.className = className;
  }

  @Override
  public String getLabel() {
    return this.label;
  }

  public Class getClassName() {
    return className;
  }
}
