/*
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
package io.trino.plugin.hive;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import io.trino.plugin.hive.metastore.HiveMetastore;
import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Optional;

import static com.google.inject.util.Modules.EMPTY_MODULE;
import static java.util.Objects.requireNonNull;

public class TestingHivePlugin
        implements Plugin
{
    private final HiveMetastore metastore;
    private final Module module;
    private final Optional<CachingDirectoryLister> cachingDirectoryLister;

    public TestingHivePlugin(HiveMetastore metastore)
    {
        this(metastore, EMPTY_MODULE, Optional.empty());
    }

    public TestingHivePlugin(HiveMetastore metastore, Module module, Optional<CachingDirectoryLister> cachingDirectoryLister)
    {
        this.metastore = requireNonNull(metastore, "metastore is null");
        this.module = requireNonNull(module, "module is null");
        this.cachingDirectoryLister = requireNonNull(cachingDirectoryLister, "cachingDirectoryLister is null");
    }

    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new TestingHiveConnectorFactory(metastore, module, cachingDirectoryLister));
    }
}
