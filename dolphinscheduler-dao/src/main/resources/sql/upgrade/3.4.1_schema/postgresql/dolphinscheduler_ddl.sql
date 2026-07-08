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

CREATE SEQUENCE IF NOT EXISTS t_ds_task_instance_context_id_seq;
ALTER TABLE t_ds_task_instance_context
ALTER COLUMN id SET DEFAULT nextval('t_ds_task_instance_context_id_seq'::regclass);

CREATE TABLE IF NOT EXISTS t_ds_platform_tenant (
  id int NOT NULL,
  tenant_code varchar(64) NOT NULL,
  tenant_name varchar(64) NOT NULL,
  description varchar(255) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS unique_platform_tenant_code on t_ds_platform_tenant (tenant_code);

CREATE TABLE IF NOT EXISTS t_ds_platform_tenant_user (
  id int NOT NULL,
  platform_tenant_id int NOT NULL,
  user_id int NOT NULL,
  create_time timestamp DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS unique_platform_tenant_user on t_ds_platform_tenant_user (platform_tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_platform_tenant_user_user_id on t_ds_platform_tenant_user (user_id);

CREATE SEQUENCE IF NOT EXISTS t_ds_platform_tenant_id_sequence;
ALTER TABLE t_ds_platform_tenant ALTER COLUMN id SET DEFAULT NEXTVAL('t_ds_platform_tenant_id_sequence');

CREATE SEQUENCE IF NOT EXISTS t_ds_platform_tenant_user_id_sequence;
ALTER TABLE t_ds_platform_tenant_user ALTER COLUMN id SET DEFAULT NEXTVAL('t_ds_platform_tenant_user_id_sequence');

INSERT INTO t_ds_platform_tenant(id, tenant_code, tenant_name, description, create_time, update_time)
VALUES (1, 'default', 'default', 'default platform tenant', now(), now())
ON CONFLICT (tenant_code) DO NOTHING;

INSERT INTO t_ds_platform_tenant_user(platform_tenant_id, user_id, create_time, update_time)
SELECT 1, id, now(), now() FROM t_ds_user
ON CONFLICT (platform_tenant_id, user_id) DO NOTHING;

SELECT setval('t_ds_platform_tenant_id_sequence', (SELECT COALESCE(MAX(id), 1) FROM t_ds_platform_tenant));
SELECT setval('t_ds_platform_tenant_user_id_sequence', (SELECT COALESCE(MAX(id), 1) FROM t_ds_platform_tenant_user));

ALTER TABLE t_ds_project ADD COLUMN IF NOT EXISTS platform_tenant_id int DEFAULT 1;
UPDATE t_ds_project SET platform_tenant_id = 1 WHERE platform_tenant_id IS NULL;
DROP INDEX IF EXISTS unique_name;
CREATE UNIQUE INDEX IF NOT EXISTS unique_name on t_ds_project (platform_tenant_id, name);

ALTER TABLE t_ds_datasource ADD COLUMN IF NOT EXISTS platform_tenant_id int DEFAULT 1;
UPDATE t_ds_datasource SET platform_tenant_id = 1 WHERE platform_tenant_id IS NULL;
ALTER TABLE t_ds_datasource DROP CONSTRAINT IF EXISTS t_ds_datasource_name_un;
CREATE UNIQUE INDEX IF NOT EXISTS t_ds_datasource_name_un on t_ds_datasource (platform_tenant_id, name, type);

ALTER TABLE t_ds_session ADD COLUMN IF NOT EXISTS platform_tenant_id int DEFAULT 1;
