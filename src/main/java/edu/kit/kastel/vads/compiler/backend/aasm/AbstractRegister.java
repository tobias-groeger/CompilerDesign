package edu.kit.kastel.vads.compiler.backend.aasm;

public record AbstractRegister(int id) implements AbstractOperand {
    public static AbstractRegister ret() {
        return new AbstractRegister(-1);
    }

    @Override
    public String toString() {
        if (this.equals(ret())) {
            return "%ret";
        }
        return "%" + id();
    }
}
