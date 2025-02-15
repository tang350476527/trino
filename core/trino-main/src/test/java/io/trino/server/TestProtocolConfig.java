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
package io.trino.server;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestProtocolConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(ProtocolConfig.class)
                .setAlternateHeaderName(null)
                .setPreparedStatementCompressionThreshold(2 * 1024)
                .setPreparedStatementCompressionMinimalGain(512));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("protocol.v1.alternate-header-name", "taco")
                .put("protocol.v1.prepared-statement-compression.length-threshold", "412")
                .put("protocol.v1.prepared-statement-compression.min-gain", "0")
                .buildOrThrow();

        ProtocolConfig expected = new ProtocolConfig()
                .setAlternateHeaderName("taco")
                .setPreparedStatementCompressionThreshold(412)
                .setPreparedStatementCompressionMinimalGain(0);

        assertFullMapping(properties, expected);
    }
}
