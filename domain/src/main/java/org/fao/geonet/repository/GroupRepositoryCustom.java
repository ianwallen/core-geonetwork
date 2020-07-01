/*
 * Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.repository;

import org.fao.geonet.domain.Group;
import org.fao.geonet.domain.ReservedGroup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Custom (non-spring-data) query methods for {@link Group} entities
 *
 * @author Jesse
 */
public interface GroupRepositoryCustom {
    /**
     * Find the group with the given groupId (where groupId is a string).  The string will be converted
     * to an integer for making the query.
     *
     * @param groupId the groupid.
     * @return the group with the given groupid
     */
    @Nullable
    Group findOne(@Nonnull String groupId);

    /**
     * Find a group given one of the Reserved groups enumeration values.
     *
     * @param groupEnum one of the Reserved groups enumeration values.
     * @return the actual group.
     */
    @Nonnull
    public Group findReservedGroup(@Nonnull ReservedGroup groupEnum);

    @Nonnull
    public List<Integer> findIds();
}
