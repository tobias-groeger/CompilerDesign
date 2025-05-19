package edu.kit.kastel.vads.compiler.backend.aasm;

public record AbstractImmediate(int value) implements AbstractOperand {
    @Override
    public String toString() {
        return "$" + value();
    }
}
