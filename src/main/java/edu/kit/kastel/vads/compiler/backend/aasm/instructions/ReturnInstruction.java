package edu.kit.kastel.vads.compiler.backend.aasm.instructions;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.List;

public record ReturnInstruction() implements AbstractInstruction {
    @Override
    public List<AbstractRegister> allRegisters() {
        return List.of(AbstractRegister.ret());
    }

    @Override
    public String toString() {
        return "ret %ret";
    }

}
