package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.List;

public record ActualRegister(int id) implements Register {

    private static final List<String> REGISTERS =
            List.of(/*"rax",*/ "rbx", "rcx",/* "rdx",*/ "rsi", "rdi", /*"rbp", "rsp",*/ "r8", "r9", "r10", "r11", "r12", "r13", "r14",
                               "r15");

    @Override
    public String toString() {

        if (id < 0 || id >= REGISTERS.size()) {
            throw new UnsupportedOperationException("Too many registers required: " + (id + 1));
        }
        return "%" + REGISTERS.get(id());
    }

}
