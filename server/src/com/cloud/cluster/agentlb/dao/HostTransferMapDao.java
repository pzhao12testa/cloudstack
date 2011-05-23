/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.cloud.cluster.agentlb.dao;

import java.util.Date;
import java.util.List;

import com.cloud.cluster.agentlb.HostTransferMapVO;
import com.cloud.cluster.agentlb.HostTransferMapVO.HostTransferState;
import com.cloud.utils.db.GenericDao;

public interface HostTransferMapDao extends GenericDao<HostTransferMapVO, Long> {

    List<HostTransferMapVO> listHostsLeavingCluster(long clusterId);

    List<HostTransferMapVO> listHostsJoiningCluster(long clusterId);

    HostTransferMapVO startAgentTransfering(long hostId, long currentOwner, long futureOwner);

    boolean completeAgentTransfering(long hostId, boolean success);
    
    List<HostTransferMapVO> listBy(long futureOwnerId, HostTransferState state);
    
    boolean isActive(long hostId, Date cutTime);
}
