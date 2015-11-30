//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.hypervisor.xenserver.resource.wrapper.xen610;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.MigrateWithStorageSendAnswer;
import com.cloud.agent.api.MigrateWithStorageSendCommand;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.hypervisor.xenserver.resource.XenServer610Resource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.exception.CloudRuntimeException;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Network;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Task;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VIF;
import com.xensource.xenapi.VM;

@ResourceWrapper(handles =  MigrateWithStorageSendCommand.class)
public final class XenServer610MigrateWithStorageSendCommandWrapper extends CommandWrapper<MigrateWithStorageSendCommand, Answer, XenServer610Resource> {

    private static final Logger s_logger = Logger.getLogger(XenServer610MigrateWithStorageSendCommandWrapper.class);

    @Override
    public Answer execute(final MigrateWithStorageSendCommand command, final XenServer610Resource xenServer610Resource) {
        final Connection connection = xenServer610Resource.getConnection();

        final VirtualMachineTO vmSpec = command.getVirtualMachine();
        final Map<VolumeTO, String> volumeToSr = command.getVolumeToSr();
        final Map<NicTO, String> nicToNetwork = command.getNicToNetwork();
        final Map<String, String> token = command.getToken();
        final String vmName = vmSpec.getName();

        Task task = null;
        try {

            // In a cluster management server setup, the migrate with storage receive and send
            // commands and answers may have to be forwarded to another management server. This
            // happens when the host/resource on which the command has to be executed is owned
            // by the second management server. The serialization/deserialization of the command
            // and answers fails as the xapi SR and Network class type isn't understand by the
            // agent attache. Seriliaze the SR and Network objects here to a string and pass in
            // the answer object. It'll be deserialzed and object created in migrate with
            // storage send command execution.
            Gson gson = new Gson();
            final Map<String, String> other = new HashMap<String, String>();
            other.put("live", "true");

            // Create the vdi map which tells what volumes of the vm need to go
            // on which sr on the destination.
            final Map<VDI, SR> vdiMap = new HashMap<VDI, SR>();
            for (final Map.Entry<VolumeTO, String> entry : volumeToSr.entrySet()) {
                SR sr = gson.fromJson(entry.getValue(), SR.class);
                VDI vdi = xenServer610Resource.getVDIbyUuid(connection, entry.getKey().getPath());
                vdiMap.put(vdi, sr);
            }

            final Set<VM> vms = VM.getByNameLabel(connection, vmSpec.getName());
            VM vmToMigrate = null;
            if (vms != null) {
                vmToMigrate = vms.iterator().next();
            }

            // Create the vif map.
            final Map<VIF, Network> vifMap = new HashMap<VIF, Network>();
            for (final Map.Entry<NicTO, String> entry : nicToNetwork.entrySet()) {
                Network network = gson.fromJson(entry.getValue(), Network.class);
                VIF vif = xenServer610Resource.getVifByMac(connection, vmToMigrate, entry.getKey().getMac());
                vifMap.put(vif, network);
            }

            // Check migration with storage is possible.
            task = vmToMigrate.assertCanMigrateAsync(connection, token, true, vdiMap, vifMap, other);
            try {
                // poll every 1 seconds.
                final long timeout = xenServer610Resource.getMigrateWait() * 1000L;
                xenServer610Resource.waitForTask(connection, task, 1000, timeout);
                xenServer610Resource.checkForSuccess(connection, task);
            } catch (final Types.HandleInvalid e) {
                s_logger.error("Error while checking if vm " + vmName + " can be migrated.", e);
                throw new CloudRuntimeException("Error while checking if vm " + vmName + " can be migrated.", e);
            }

            // Migrate now.
            task = vmToMigrate.migrateSendAsync(connection, token, true, vdiMap, vifMap, other);
            try {
                // poll every 1 seconds.
                final long timeout = xenServer610Resource.getMigrateWait() * 1000L;
                xenServer610Resource.waitForTask(connection, task, 1000, timeout);
                xenServer610Resource.checkForSuccess(connection, task);
            } catch (final Types.HandleInvalid e) {
                s_logger.error("Error while migrating vm " + vmName, e);
                throw new CloudRuntimeException("Error while migrating vm " + vmName, e);
            }

            final Set<VolumeTO> volumeToSet = null;
            return new MigrateWithStorageSendAnswer(command, volumeToSet);
        } catch (final CloudRuntimeException e) {
            s_logger.error("Migration of vm " + vmName + " with storage failed due to " + e.toString(), e);
            return new MigrateWithStorageSendAnswer(command, e);
        } catch (final Exception e) {
            s_logger.error("Migration of vm " + vmName + " with storage failed due to " + e.toString(), e);
            return new MigrateWithStorageSendAnswer(command, e);
        } finally {
            if (task != null) {
                try {
                    task.destroy(connection);
                } catch (final Exception e) {
                    s_logger.debug("Unable to destroy task " + task.toString() + " on host " + xenServer610Resource.getHost().getUuid() + " due to " + e.toString());
                }
            }
        }
    }
}