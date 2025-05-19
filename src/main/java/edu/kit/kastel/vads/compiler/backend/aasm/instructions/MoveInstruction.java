package edu.kit.kastel.vads.compiler.backend.aasm.instructions;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractOperand;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.ArrayList;
import java.util.List;

public record MoveInstruction(AbstractRegister dest, AbstractOperand src) implements AbstractInstruction {
    @Override
    public List<AbstractRegister> allRegisters() {
        List<AbstractRegister> reg = new ArrayList<>();
        reg.add(dest());
        if (src() instanceof AbstractRegister srcReg) reg.add(srcReg);
        return reg;
    }

    @Override
    public String toString() {
        return dest() + " <- " + src();
    }
}
