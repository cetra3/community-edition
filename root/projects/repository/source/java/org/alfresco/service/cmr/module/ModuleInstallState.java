/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.service.cmr.module;

/**
 * Enum used to indicate the install state of a module.
 * 
 * @author Roy Wetherall
 */
public enum ModuleInstallState
{
    /** The state of the module is unknown */
    UNKNOWN,
    /** The module is installed */
    INSTALLED,
    /** The module is disabled */
    DISABLED,
    /** The module has been uninstalled */
    UNINSTALLED;
}
