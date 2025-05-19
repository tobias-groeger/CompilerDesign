package edu.kit.kastel.vads.compiler.backend.aasm.instructions;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.List;

public record NopInstruction() implements AbstractInstruction {
    @Override
    public List<AbstractRegister> allRegisters() {
        return List.of();
    }

    @Override
    public String toString() {
        return "nop";
    }
}
