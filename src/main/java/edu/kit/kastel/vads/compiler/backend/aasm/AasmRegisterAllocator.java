package edu.kit.kastel.vads.compiler.backend.aasm;

public class AasmRegisterAllocator {
    private int id = 0;

    public AbstractRegister allocate() {
        return new AbstractRegister(this.id++);
    }

}
