package edu.kit.kastel.vads.compiler.backend.aasm.instructions;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.List;

public sealed interface AbstractInstruction permits BinaryInstruction, MoveInstruction, NopInstruction, ReturnInstruction {

    List<AbstractRegister> allRegisters();
    
}
