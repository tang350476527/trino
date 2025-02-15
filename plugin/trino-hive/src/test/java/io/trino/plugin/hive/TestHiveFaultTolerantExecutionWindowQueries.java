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

import io.trino.plugin.exchange.containers.MinioStorage;
import io.trino.testing.AbstractTestFaultTolerantExecutionWindowQueries;
import io.trino.testing.QueryRunner;
import org.testng.annotations.AfterClass;

import java.util.Map;

import static io.trino.plugin.exchange.containers.MinioStorage.getExchangeManagerProperties;
import static io.trino.testing.sql.TestTable.randomTableSuffix;
import static io.trino.tpch.TpchTable.getTables;

public class TestHiveFaultTolerantExecutionWindowQueries
        extends AbstractTestFaultTolerantExecutionWindowQueries
{
    private MinioStorage minioStorage;

    @Override
    protected QueryRunner createQueryRunner(Map<String, String> extraProperties)
            throws Exception
    {
        this.minioStorage = new MinioStorage("test-exchange-spooling-" + randomTableSuffix());
        minioStorage.start();

        return HiveQueryRunner.builder()
                .setExtraProperties(extraProperties)
                .setExchangeManagerProperties(getExchangeManagerProperties(minioStorage))
                .setInitialTables(getTables())
                .build();
    }

    @AfterClass(alwaysRun = true)
    public void destroy()
            throws Exception
    {
        if (minioStorage != null) {
            minioStorage.close();
            minioStorage = null;
        }
    }
}
