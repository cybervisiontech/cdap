/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.metrics.process;

import co.cask.cdap.common.metrics.MetricsScope;
import org.apache.twill.kafka.client.KafkaConsumer;

/**
 * Factory to create MessageCallback for the Metrics Processing Service. This factory interface
 * exists for simplifying object injections by guice only.
 */
public interface MessageCallbackFactory {

  KafkaConsumer.MessageCallback create(KafkaConsumerMetaTable metaTable, MetricsScope scope);
}
