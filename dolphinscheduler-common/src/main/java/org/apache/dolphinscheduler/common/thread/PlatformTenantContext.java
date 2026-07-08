/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.common.thread;

public final class PlatformTenantContext {

    private static final ThreadLocal<Integer> PLATFORM_TENANT_ID = new ThreadLocal<>();

    private PlatformTenantContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void setPlatformTenantId(Integer platformTenantId) {
        PLATFORM_TENANT_ID.set(platformTenantId);
    }

    public static Integer getPlatformTenantId() {
        return PLATFORM_TENANT_ID.get();
    }

    public static void removePlatformTenantId() {
        PLATFORM_TENANT_ID.remove();
    }
}
