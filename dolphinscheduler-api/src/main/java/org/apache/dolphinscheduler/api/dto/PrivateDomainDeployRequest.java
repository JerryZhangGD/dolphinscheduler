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

package org.apache.dolphinscheduler.api.dto;

import lombok.Data;

@Data
public class PrivateDomainDeployRequest {

    private String sshHost;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private String sshPrivateKey;

    private String deployIp;

    private String dbType;

    private String dbHost;

    private String dbPort;

    private String dbName;

    private String dbUser;

    private String dbPassword;

    private String dbUrl;

    private String deployPath;

    private String processCheckCommand;

    private String deployCommand;

    private String nginxConfigCommand;

    private String nginxReloadCommand;
}
