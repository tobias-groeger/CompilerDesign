package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        for (IrGraph graph : program) {
            GraphColoringRegisterAllocator allocator = new GraphColoringRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);
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

            generateForGraph(graph, builder, registers);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        switch (node) {
            case AddNode add -> binary(builder, registers, add, "addq");
            case SubNode sub -> binary(builder, registers, sub, "subq");
            case MulNode mul -> {

                builder.repeat(" ", 2)
                        // move lhs into %rax
                        .append("movq ").append(registers.get(predecessorSkipProj(mul, BinaryOperationNode.LEFT))).append(", %rax\n");
                builder.repeat(" ", 2)
                        // RDX:RAX = %rax * rhs
                        .append("mulq ").append(registers.get(predecessorSkipProj(mul, BinaryOperationNode.RIGHT))).append("\n");

                builder.repeat(" ", 2)
                        // move %rax into dest
                        .append("movq %rax, ").append(registers.get(mul));

            }
            case DivNode div -> div(builder, registers, div, false);
            case ModNode mod -> div(builder, registers, mod, true);
            case ReturnNode r -> {
                builder.repeat(" ", 2).append("movq ").append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT))).append(", %rax\n");
                builder.repeat(" ", 2).append("ret");
            }
            case ConstIntNode c -> builder.repeat(" ", 2).append("movq $").append(c.value()).append(", ").append(registers.get(c));
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        builder.append("\n");
    }

    private static void binary(StringBuilder builder, Map<Node, Register> registers, BinaryOperationNode node, String opcode) {
        builder.repeat(" ", 2)
                // move rhs into dest
                .append("movq ").append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT))).append(", ")
                .append(registers.get(node)).append("\n");

        builder.repeat(" ", 2)
                // dest = dest <op>> rhs
                .append(opcode).append(" ").append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT))).append(", ")
                .append(registers.get(node));
    }

    private static void div(StringBuilder builder, Map<Node, Register> registers, BinaryOperationNode node, boolean modulo) {
        builder.repeat(" ", 2)
                // move lhs into %rax
                .append("movq ").append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT))).append(", %rax\n")
                // zero out %rdx
                .append("  xorq %rdx, %rdx\n");
        builder.repeat(" ", 2)
                // RAX = RDX:RAX / rhs
                // RDX = RDX:RAX % rhs
                .append("divq ").append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT))).append("\n");

        String reg = modulo ? "%rdx" : "%rax";
        builder.repeat(" ", 2)
                // move %rax into dest
                .append("movq ").append(reg).append(", ").append(registers.get(node));
    }
}
