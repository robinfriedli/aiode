package net.robinfriedli.botify.persist.customchange;

import net.robinfriedli.botify.command.PermissionTarget;

public class PermissionTargetTypeInitialValues extends InsertEnumLookupValuesChange<PermissionTarget.TargetType> {

    @Override
    protected PermissionTarget.TargetType[] getValues() {
        return PermissionTarget.TargetType.values();
    }

    @Override
    protected String getTableName() {
        return "permission_type";
    }

}
