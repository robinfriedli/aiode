package net.robinfriedli.aiode.persist.customchange;

import net.robinfriedli.aiode.command.PermissionTarget;

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
