package mb.statix.concurrent.solver.test;

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import mb.nabl2.terms.ITerm;
import mb.statix.concurrent.actors.futures.CompletableFuture;
import mb.statix.concurrent.actors.futures.IFuture;
import mb.statix.concurrent.p_raffrayi.ITypeChecker;
import mb.statix.concurrent.p_raffrayi.ITypeCheckerContext;
import mb.statix.concurrent.solver.UnitTypeChecker;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.spec.Rule;
import mb.statix.spec.Spec;

public class SubProjectTypeChecker implements ITypeChecker<Scope, ITerm, ITerm, SolverResult> {

    private final Map<String, Rule> units;
    private final Spec spec;
    private final IDebugContext debug;

    public SubProjectTypeChecker(Map<String, Rule> units, Spec spec, IDebugContext debug) {
        this.units = units;
        this.spec = spec;
        this.debug = debug;
    }

    @Override public IFuture<SolverResult> run(ITypeCheckerContext<Scope, ITerm, ITerm, SolverResult> context,
            @Nullable Scope root) {
        context.initRoot(root, ImmutableSet.of(), true);
        for(Entry<String, Rule> entry : units.entrySet()) {
            final String id = entry.getKey();
            final Rule rule = entry.getValue();
            context.add(id, new UnitTypeChecker(rule, spec, debug), root);
        }
        context.closeScope(root);
        return CompletableFuture.completedFuture(SolverResult.of(spec));
    }

}