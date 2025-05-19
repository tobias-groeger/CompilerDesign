package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import java.util.List;

public record ActualRegister(int id) implements Comparable<ActualRegister> {

    private static final List<String> REGISTERS =
            List.of("rax", "rbx", "rcx", "rdx", "rsi", "rdi", /*"rbp", "rsp",*/ "r8", "r9", "r10",/* "r11", "r12", */"r13", "r14", "r15");

    public static ActualRegister eax() {
        return new ActualRegister(0);
    }

    public static ActualRegister edx() {
        return new ActualRegister(3);
    }

    public static ActualRegister spare() {
        return new ActualRegister(-1);
    }

    public static ActualRegister spare(int index) {
        return new ActualRegister(-index);
    }

    public int spillIndex() {
        return id - REGISTERS.size();
    }

    @Override
    public String toString() {
        if (id == -1) {
            return "%r11";
        }
        if (id == -2) {
            return "%r12";
        }

        if (id < 0 || id >= REGISTERS.size()) {
            throw new UnsupportedOperationException("Too many registers required: " + (id + 1));
        }
        return "%" + REGISTERS.get(id());
    }

    @Override
    public int compareTo(ActualRegister other) {
        return id() - other.id();
    }
}
