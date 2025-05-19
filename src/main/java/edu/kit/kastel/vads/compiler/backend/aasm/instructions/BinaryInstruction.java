package edu.kit.kastel.vads.compiler.backend.aasm.instructions;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractOperand;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.ArrayList;
import java.util.List;

public record BinaryInstruction(Op op, AbstractRegister dest, AbstractOperand lhs, AbstractOperand rhs) implements AbstractInstruction {
    public enum Op {
        ADD, DIV, MOD, MUL, SUB
    }

    @Override
    public List<AbstractRegister> allRegisters() {
        List<AbstractRegister> reg = new ArrayList<>();
        reg.add(dest());
        if (lhs() instanceof AbstractRegister lhsReg) reg.add(lhsReg);
        if (rhs() instanceof AbstractRegister rhsReg) reg.add(rhsReg);
        return reg;
    }

    private String stringOp() {
        return switch (op()) {
            case ADD -> " + ";
            case DIV -> " / ";
            case MOD -> " % ";
            case MUL -> " * ";
            case SUB -> " - ";
        };
    }

    @Override
    public String toString() {
        return dest() + " <- " + lhs() + stringOp() + rhs();
    }
}
