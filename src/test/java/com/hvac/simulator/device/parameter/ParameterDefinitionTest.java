package com.hvac.simulator.device.parameter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParameterDefinitionTest {

    @Test
    void definitionRequiresMatchingTypeUnitConstraintAndDefault() {
        var definition = new ParameterDefinition(
                "rated-power",
                "额定功率",
                ParameterType.DECIMAL,
                new DecimalParameterValue(100.0, UnitCode.KILOWATT),
                UnitCode.KILOWATT,
                new DecimalRange(0.0, 500.0),
                ParameterUsage.CONFIGURABLE_CALCULATION);

        assertTrue(definition.modifiable());
        assertTrue(definition.usedInCalculation());
        assertThrows(IllegalArgumentException.class, () -> new ParameterDefinition(
                "rated-power",
                "额定功率",
                ParameterType.INTEGER,
                new DecimalParameterValue(100.0, UnitCode.KILOWATT),
                UnitCode.KILOWATT,
                new IntegerRange(0, 500),
                ParameterUsage.CONFIGURABLE_CALCULATION));
    }

    @Test
    void enumConstraintDefensivelyCopiesAllowedValues() {
        var allowed = new ArrayList<>(List.of("AUTO", "MANUAL"));
        var constraint = new AllowedEnumValues(allowed);
        allowed.clear();

        assertTrue(constraint.accepts(new EnumParameterValue("AUTO")));
        assertFalse(constraint.accepts(new EnumParameterValue("OFF")));
        assertThrows(UnsupportedOperationException.class, () -> constraint.values().clear());
    }

    @Test
    void usageSeparatesConfigurableFixedAndUnusedParameters() {
        assertTrue(ParameterUsage.CONFIGURABLE_CALCULATION.modifiable());
        assertTrue(ParameterUsage.FIXED_CALCULATION.usedInCalculation());
        assertFalse(ParameterUsage.FIXED_CALCULATION.modifiable());
        assertFalse(ParameterUsage.NOT_USED_IN_CALCULATION.usedInCalculation());
    }
}
