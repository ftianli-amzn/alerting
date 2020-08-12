/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.alerting.client

import com.amazon.opendistroforelasticsearch.alerting.core.model.HttpInput
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.ssl.SSLContextBuilder
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.unit.ByteSizeUnit
import org.elasticsearch.common.unit.TimeValue
import java.io.IOException
import java.security.*
import javax.net.ssl.SSLContext


/**
 * This class takes [HttpInput] and performs GET requests to given URIs.
 */
class HttpInputClient {

    // TODO: If possible, these settings should be implemented as changeable via the "_cluster/settings" API.
    private val CONNECTION_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(5).millis().toInt()
    private val REQUEST_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(10).millis().toInt()
    private val SOCKET_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(10).millis().toInt()
    val MAX_CONTENT_LENGTH = ByteSizeUnit.MB.toBytes(100)

    val client = createHttpClient()
    val newRestClient = createWithSelfSignedAndCreds()

    /**
     * Create [CloseableHttpAsyncClient] as a [PrivilegedAction] in order to avoid [java.net.NetPermission] error.
     */
    private fun createHttpClient(): CloseableHttpAsyncClient {
        val config = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT_MILLISECONDS)
                .setConnectionRequestTimeout(REQUEST_TIMEOUT_MILLISECONDS)
                .setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS)
                .build()

        return AccessController.doPrivileged(PrivilegedAction<CloseableHttpAsyncClient>({
            HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .useSystemProperties()
                    .build()
        } as () -> CloseableHttpAsyncClient))
    }

    @Throws(IOException::class)
    private fun createWithSelfSignedAndCreds(): RestClient? {
        val sslContext: SSLContext
        sslContext = try {
            val sslbuilder = SSLContextBuilder()
            sslbuilder.loadTrustMaterial(null, TrustSelfSignedStrategy())
            sslbuilder.build()
        } catch (e: KeyManagementException) {
            throw IOException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw IOException(e)
        } catch (e: KeyStoreException) {
            throw IOException(e)
        }
        val credentialsProvider: CredentialsProvider = BasicCredentialsProvider()
        credentialsProvider.setCredentials(AuthScope.ANY,
                UsernamePasswordCredentials("admin", "admin"))
        return RestClient.builder(HttpHost("localhost", 9200, "https"))
                .setHttpClientConfigCallback(object : RestClientBuilder.HttpClientConfigCallback {
                    override fun customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder? {
                        httpClientBuilder.setSSLContext(sslContext)
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                    }
                }).build()
    }
}
