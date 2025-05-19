package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

public interface RegisterAllocator {

    ActualRegister registerOf(AbstractRegister register);

}
