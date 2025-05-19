package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.Constants;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractImmediate;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractOperand;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.*;
import edu.kit.kastel.vads.compiler.backend.optimization.Optimization;
import edu.kit.kastel.vads.compiler.backend.optimization.ValuePropagation;
import edu.kit.kastel.vads.compiler.backend.x86_64.analysis.FactDatabase;
import edu.kit.kastel.vads.compiler.backend.x86_64.analysis.LivenessAnalysis;
import edu.kit.kastel.vads.compiler.backend.x86_64.regalloc.*;
import edu.kit.kastel.vads.compiler.ir.IrGraph;

import java.util.List;

public class CodeGenerator {

    private static final List<Optimization> OPTIMIZATIONS = List.of(new ValuePropagation());

    public String generateCode(List<IrGraph> program) {
        var aasmGenerator = new edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator();
        var aasm = aasmGenerator.generateCode(program);

        boolean changes;
        do {
            changes = false;
            for (var i : OPTIMIZATIONS) {
                changes |= i.optimize(aasm);
            }
        } while (changes);

        if (Constants.DEBUG) {
            System.out.println("Optimized AASM");
            for (int i = 0; i < aasm.size(); i++) {
                System.out.println((i + 1) + ": " + aasm.get(i));
            }
            System.out.println("===");
        }

        FactDatabase database = new FactDatabase(aasm.size());
        LivenessAnalysis liveness = new LivenessAnalysis(database);
        liveness.analyse(aasm);

        InterferenceGraph interferenceGraph = new InterferenceGraphGenerator().generateGraph(aasm, database);
        GraphColoringRegisterAllocator allocator = new GraphColoringRegisterAllocator(aasm, interferenceGraph);

        StringBuilder builder = new StringBuilder();
        builder.append("""
                               .global main
                               .global _main
                               .text
                               
                               main:
                               call _main
                               
                               # move the return value into the first argument for the syscall
                               movq %rax, %rdi
                               # move the exit syscall number into rax
                               movq $0x3C, %rax
                               syscall
                               
                               _main:
                                 # start of generated assembly
                               
                               """);

        for (var i : aasm) {
            emitCode(builder, i, allocator);
        }

        return builder.toString();
    }

    private static String mapOp(BinaryInstruction.Op op) {
        return switch (op) {
            case BinaryInstruction.Op.ADD -> "addq";
            case BinaryInstruction.Op.DIV -> "idivq";
            case BinaryInstruction.Op.MOD -> "idivq";
            case BinaryInstruction.Op.MUL -> "mulq";
            case BinaryInstruction.Op.SUB -> "subq";
        };
    }

    private String mapRegOrImm(AbstractOperand operand, RegisterAllocator allocator) {
        if (operand instanceof AbstractImmediate(int value)) {
            return "$" + value;
        } else if (operand instanceof AbstractRegister reg) {
            return allocator.registerOf(reg).toString();
        }
        throw new UnsupportedOperationException();
    }

    private void emitCode(StringBuilder b, AbstractInstruction instruction, RegisterAllocator allocator) {
        switch (instruction) {
            // binary instruction with special registers (mul, div, mod)
            case BinaryInstruction(BinaryInstruction.Op op, var dest, AbstractRegister lhs, AbstractRegister rhs) when
                    op == BinaryInstruction.Op.MUL || op == BinaryInstruction.Op.DIV || op == BinaryInstruction.Op.MOD -> {

                if (!allocator.registerOf(lhs).equals(ActualRegister.eax())) {
                    b.append("movq ").append(allocator.registerOf(lhs)).append(", %rax\n");
                }
                b.append("cltd\n");
                b.append(mapOp(op)).append(" ").append(allocator.registerOf(rhs)).append("\n");

                if ((op == BinaryInstruction.Op.MUL || op == BinaryInstruction.Op.DIV) &&
                    !allocator.registerOf(dest).equals(ActualRegister.eax())) {
                    // mul places lower bits of result in eax, div has quotient in eax
                    b.append("movq %rax, ").append(allocator.registerOf(dest)).append("\n");
                } else if (op == BinaryInstruction.Op.MOD && !allocator.registerOf(dest).equals(ActualRegister.edx())) {
                    // div has remainder in edx
                    b.append("movq %rdx, ").append(allocator.registerOf(dest)).append("\n");
                }
            }
            // binary instruction with lhs and rhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var dest, AbstractRegister lhs, AbstractRegister rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister d = allocator.registerOf(dest);
                ActualRegister x = allocator.registerOf(lhs);
                ActualRegister y = allocator.registerOf(rhs);

                if (d.equals(x) && d.equals(y)) {
                    // d <- d x d
                    b.append(mapOp(op)).append(" ").append(d).append(", ").append(d).append("\n");
                } else if (d.equals(x)) {
                    // d <- d x %
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                } else if (d.equals(y) && op == BinaryInstruction.Op.SUB) {
                    // d <- % x d

                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    // if dst == rhs, we have to first save rhs -> spare
                    b.append("movq ").append(d).append(", ").append(ActualRegister.spare()).append("\n");
                    b.append("movq ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(ActualRegister.spare()).append(", ").append(d).append("\n");
                } else if (d.equals(y) && op == BinaryInstruction.Op.ADD) {
                    // d <- % + d
                    b.append(mapOp(op)).append(" ").append(x).append(", ").append(d).append("\n");
                } else {
                    b.append("movq ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                }
            } // binary instruction with lhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var dest, AbstractRegister lhs, AbstractImmediate rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister d = allocator.registerOf(dest);
                ActualRegister x = allocator.registerOf(lhs);

                if (d.equals(x)) {
                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(d).append("\n");
                } else {
                    b.append("movq ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(d).append("\n");
                }
            }
            // binary instruction with rhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var dest, AbstractImmediate lhs, AbstractRegister rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister d = allocator.registerOf(dest);
                ActualRegister y = allocator.registerOf(rhs);

                if (d.equals(y) && op == BinaryInstruction.Op.SUB) {
                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    // if dst == rhs, we have to first save rhs -> spare
                    b.append("movq ").append(d).append(", ").append(ActualRegister.spare()).append("\n");
                    b.append("movq $").append(lhs).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(ActualRegister.spare()).append(", ").append(d).append("\n");
                } else {
                    b.append("movq $").append(lhs).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                }
            }
            // binary instruction with two immediates
            case BinaryInstruction(BinaryInstruction.Op op, var dest, AbstractImmediate lhs, AbstractImmediate rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                // for subtraction: 'sub src dst' <==> dst := dst - src
                b.append("movq $").append(lhs.value()).append(", ").append(allocator.registerOf(dest)).append("\n");
                b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(allocator.registerOf(dest)).append("\n");
            }
            case MoveInstruction(var dest, var src) ->
                    b.append("movq ").append(mapRegOrImm(src, allocator)).append(", ").append(allocator.registerOf(dest)).append("\n");
            case NopInstruction _ -> {
            }
            case ReturnInstruction _ -> b.append("ret\n");
            default -> throw new UnsupportedOperationException("No instructions for " + instruction + " available!");
        }
    }

    private ActualRegister unspill(ActualRegister reg, StringBuilder b, int index) {
        int address = reg.spillIndex();
        if (address < 0) {
            return reg;
        }

        ActualRegister spillRegister = ActualRegister.spare(index);
        b.append("movq ").append(address * 8).append("(%rsp), ").append(spillRegister).append("\n");

        return spillRegister;
    }
}
