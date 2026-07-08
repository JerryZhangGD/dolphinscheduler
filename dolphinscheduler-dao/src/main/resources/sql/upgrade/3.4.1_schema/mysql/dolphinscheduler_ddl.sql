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

ALTER TABLE `t_ds_serial_command`
MODIFY COLUMN `workflow_definition_code` BIGINT(20) NOT NULL COMMENT 'workflow definition code';

CREATE TABLE IF NOT EXISTS `t_ds_platform_tenant` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'platform tenant id',
  `tenant_code` varchar(64) NOT NULL COMMENT 'platform tenant code',
  `tenant_name` varchar(64) NOT NULL COMMENT 'platform tenant name',
  `description` varchar(255) DEFAULT NULL COMMENT 'description',
  `create_time` datetime DEFAULT NULL COMMENT 'create time',
  `update_time` datetime DEFAULT NULL COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_platform_tenant_code` (`tenant_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE = utf8_bin;

CREATE TABLE IF NOT EXISTS `t_ds_platform_tenant_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'key',
  `platform_tenant_id` int(11) NOT NULL COMMENT 'platform tenant id',
  `user_id` int(11) NOT NULL COMMENT 'user id',
  `create_time` datetime DEFAULT NULL COMMENT 'create time',
  `update_time` datetime DEFAULT NULL COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_platform_tenant_user` (`platform_tenant_id`, `user_id`),
  KEY `idx_platform_tenant_user_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE = utf8_bin;

INSERT IGNORE INTO `t_ds_platform_tenant`(id, tenant_code, tenant_name, description, create_time, update_time)
VALUES (1, 'default', 'default', 'default platform tenant', current_timestamp, current_timestamp);

INSERT IGNORE INTO `t_ds_platform_tenant_user`(platform_tenant_id, user_id, create_time, update_time)
SELECT 1, id, current_timestamp, current_timestamp FROM `t_ds_user`;

ALTER TABLE `t_ds_project`
ADD COLUMN `platform_tenant_id` int(11) DEFAULT 1 COMMENT 'platform tenant id' AFTER `user_id`;

UPDATE `t_ds_project` SET `platform_tenant_id` = 1 WHERE `platform_tenant_id` IS NULL;

ALTER TABLE `t_ds_project` DROP INDEX `unique_name`;
ALTER TABLE `t_ds_project` ADD UNIQUE KEY `unique_name`(`platform_tenant_id`, `name`);

ALTER TABLE `t_ds_datasource`
ADD COLUMN `platform_tenant_id` int(11) DEFAULT 1 COMMENT 'platform tenant id' AFTER `user_id`;

UPDATE `t_ds_datasource` SET `platform_tenant_id` = 1 WHERE `platform_tenant_id` IS NULL;

ALTER TABLE `t_ds_datasource` DROP INDEX `t_ds_datasource_name_un`;
ALTER TABLE `t_ds_datasource` ADD UNIQUE KEY `t_ds_datasource_name_un`(`platform_tenant_id`, `name`, `type`);

ALTER TABLE `t_ds_session`
ADD COLUMN `platform_tenant_id` int(11) DEFAULT 1 COMMENT 'platform tenant id' AFTER `user_id`;
