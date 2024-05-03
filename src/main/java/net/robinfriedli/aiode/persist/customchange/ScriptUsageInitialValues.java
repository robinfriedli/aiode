package net.robinfriedli.aiode.persist.customchange;

import net.robinfriedli.aiode.scripting.ScriptUsageType;

public class ScriptUsageInitialValues extends InsertEnumLookupValuesChange<ScriptUsageType> {

    @Override
    protected ScriptUsageType[] getValues() {
        return ScriptUsageType.values();
    }

    @Override
    protected String getTableName() {
        return "script_usage";
    }

}
