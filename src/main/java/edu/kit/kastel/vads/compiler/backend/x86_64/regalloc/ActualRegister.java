package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import java.util.List;

public record ActualRegister(int id) implements Comparable<ActualRegister> {

    private static final List<String> REGISTERS =
            List.of("eax", "ebx", "ecx", "edx", "esi", "edi", /*"ebp", "esp",*/ "r8d", "r9d", "r10d",/* "r11d", "r12d", "r13d",*/ "r14d",
                    "r15d");

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
        if (index < 0) throw new UnsupportedOperationException("Invalid spare index: " + index);
        return new ActualRegister(-index - 1);
    }

    public int spillIndex() {
        return id - REGISTERS.size();
    }

    @Override
    public String toString() {
        if (id == -1) return "%r11d";
        if (id == -2) return "%r12d";
        if (id == -3) return "%r13d";

        if (id < 0) throw new UnsupportedOperationException("Invalid register id: " + (id + 1));

        if (id < REGISTERS.size()) return "%" + REGISTERS.get(id());

        return "%spill<" + id() + ">";
    }

    @Override
    public int compareTo(ActualRegister other) {
        return id() - other.id();
    }
}
