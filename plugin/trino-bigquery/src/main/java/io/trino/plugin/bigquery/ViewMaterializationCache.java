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
package io.trino.plugin.bigquery;

import com.google.cloud.BaseServiceException;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.common.cache.CacheBuilder;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.collect.cache.NonEvictableCache;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.TableNotFoundException;

import javax.inject.Inject;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static io.trino.collect.cache.SafeCaches.buildNonEvictableCache;
import static io.trino.plugin.bigquery.BigQueryErrorCode.BIGQUERY_VIEW_DESTINATION_TABLE_CREATION_FAILED;
import static io.trino.plugin.bigquery.BigQueryUtil.convertToBigQueryException;
import static java.lang.String.format;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ViewMaterializationCache
{
    private static final Logger log = Logger.get(ViewMaterializationCache.class);

    private final NonEvictableCache<String, TableInfo> destinationTableCache;
    private final Optional<String> viewMaterializationProject;
    private final Optional<String> viewMaterializationDataset;

    @Inject
    public ViewMaterializationCache(BigQueryConfig config)
    {
        requireNonNull(config, "config is null");
        this.destinationTableCache = buildNonEvictableCache(
                CacheBuilder.newBuilder()
                        .expireAfterWrite(config.getViewsCacheTtl().toMillis(), MILLISECONDS)
                        .maximumSize(1000));
        this.viewMaterializationProject = config.getViewMaterializationProject();
        this.viewMaterializationDataset = config.getViewMaterializationDataset();
    }

    public TableInfo getCachedTable(BigQueryClient client, String query, Duration viewExpiration, TableInfo remoteTableId)
    {
        try {
            return destinationTableCache.get(query, new DestinationTableBuilder(client, viewExpiration, query, createDestinationTable(remoteTableId.getTableId())));
        }
        catch (ExecutionException e) {
            throw new TrinoException(BIGQUERY_VIEW_DESTINATION_TABLE_CREATION_FAILED, "Error creating destination table", e);
        }
    }

    private TableId createDestinationTable(TableId remoteTableId)
    {
        String project = viewMaterializationProject.orElse(remoteTableId.getProject());
        String dataset = viewMaterializationDataset.orElse(remoteTableId.getDataset());

        String name = format("_pbc_%s", randomUUID().toString().toLowerCase(ENGLISH).replace("-", ""));
        return TableId.of(project, dataset, name);
    }

    private static class DestinationTableBuilder
            implements Callable<TableInfo>
    {
        private final BigQueryClient bigQueryClient;
        private final Duration viewExpiration;
        private final String query;
        private final TableId destinationTable;

        DestinationTableBuilder(BigQueryClient bigQueryClient, Duration viewExpiration, String query, TableId destinationTable)
        {
            this.bigQueryClient = requireNonNull(bigQueryClient, "bigQueryClient is null");
            this.viewExpiration = requireNonNull(viewExpiration, "viewExpiration is null");
            this.query = requireNonNull(query, "query is null");
            this.destinationTable = requireNonNull(destinationTable, "destinationTable is null");
        }

        @Override
        public TableInfo call()
        {
            return createTableFromQuery();
        }

        private TableInfo createTableFromQuery()
        {
            log.debug("destinationTable is %s", destinationTable);
            JobInfo jobInfo = JobInfo.of(
                    QueryJobConfiguration
                            .newBuilder(query)
                            .setDestinationTable(destinationTable)
                            .build());
            log.debug("running query %s", jobInfo);
            Job job = waitForJob(bigQueryClient.create(jobInfo));
            log.debug("job has finished. %s", job);
            if (job.getStatus().getError() != null) {
                throw convertToBigQueryException(job.getStatus().getError());
            }
            // add expiration time to the table
            TableInfo createdTable = bigQueryClient.getTable(destinationTable)
                    .orElseThrow(() -> new TableNotFoundException(new SchemaTableName(destinationTable.getDataset(), destinationTable.getTable())));
            long expirationTimeMillis = createdTable.getCreationTime() + viewExpiration.toMillis();
            Table updatedTable = bigQueryClient.update(createdTable.toBuilder()
                    .setExpirationTime(expirationTimeMillis)
                    .build());
            return updatedTable;
        }

        private Job waitForJob(Job job)
        {
            try {
                return job.waitFor();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BigQueryException(BaseServiceException.UNKNOWN_CODE, format("Job %s has been interrupted", job.getJobId()), e);
            }
        }
    }
}
