package edu.kit.kastel.vads.compiler.backend.x86_64.analysis;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FactDatabase {
    private List<Set<AbstractRegister>> liveInVariables;

    public FactDatabase(int capacity) {
        // allocate one more element than lines so we can look at the line after the last
        this.liveInVariables = new ArrayList<>(capacity + 1);
        for (int i = 0; i < capacity + 1; i++) {
            this.liveInVariables.add(new HashSet<>());
        }
    }

    public int size() {
        return liveInVariables.size() - 1;
    }

    public boolean setLive(int line, AbstractRegister value) {
        return liveInVariables.get(line).add(value);
    }

    public Set<AbstractRegister> getLiveInAt(int line) {
        return Set.copyOf(liveInVariables.get(line));
    }
}
