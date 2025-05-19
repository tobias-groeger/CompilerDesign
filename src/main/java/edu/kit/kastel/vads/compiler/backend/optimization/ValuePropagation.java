package edu.kit.kastel.vads.compiler.backend.optimization;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.BinaryInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.MoveInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.NopInstruction;

import java.util.List;

public class ValuePropagation implements Optimization {
    @Override
    public boolean optimize(List<AbstractInstruction> instructions) {
        boolean modified = false;

        for (int i = 0; i < instructions.size(); i++) {
            // the move into the return register can't be optimized away
            if (instructions.get(i) instanceof MoveInstruction(AbstractRegister d, AbstractRegister s) &&
                !d.equals(AbstractRegister.ret())) {
                modified = true;

                for (int j = i + 1; j < instructions.size(); j++) {
                    switch (instructions.get(j)) {
                        case BinaryInstruction(var op, var dest, var lhs, var rhs) when lhs == d ->
                                instructions.set(j, new BinaryInstruction(op, dest, s, rhs));
                        case BinaryInstruction(var op, var dest, var lhs, var rhs) when rhs == d ->
                                instructions.set(j, new BinaryInstruction(op, dest, lhs, s));

                        default -> {
                        }
                    }
                }

                instructions.set(i, new NopInstruction());
            }
        }

        return modified;
    }
}
