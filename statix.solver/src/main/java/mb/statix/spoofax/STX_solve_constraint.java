package mb.statix.spoofax;

import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.metaborg.util.iterators.Iterables2;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.Level;
import org.metaborg.util.log.LoggerUtils;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import mb.nabl2.stratego.TermIndex;
import mb.nabl2.terms.IListTerm;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.substitution.PersistentSubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.util.ImmutableTuple2;
import mb.nabl2.util.Tuple2;
import mb.statix.solver.IConstraint;
import mb.statix.solver.ISolverResult;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.log.LoggerDebugContext;
import mb.statix.solver.log.NullDebugContext;
import mb.statix.spec.IRule;
import mb.statix.spec.Spec;
import mb.statix.taico.module.IModule;
import mb.statix.taico.module.Module;
import mb.statix.taico.module.ModuleManager;
import mb.statix.taico.solver.MState;
import mb.statix.taico.solver.ModuleSolver;
import mb.statix.taico.solver.SolverCoordinator;

public class STX_solve_constraint extends StatixPrimitive {
    private static final ILogger logger = LoggerUtils.logger(STX_solve_constraint.class);
    private static final boolean DEBUG = true;

    @Inject public STX_solve_constraint() {
        super(STX_solve_constraint.class.getSimpleName(), 2);
    }

    @Override protected Optional<? extends ITerm> call(IContext env, ITerm term, List<ITerm> terms)
            throws InterpreterException {
        final Spec spec =
                StatixTerms.spec().match(terms.get(0)).orElseThrow(() -> new InterpreterException("Expected spec."));
        reportOverlappingRules(spec);

        final String levelString =
                M.stringValue().match(terms.get(1)).orElseThrow(() -> new InterpreterException("Expected log level."));
        final @Nullable Level level = levelString.equalsIgnoreCase("None") ? null : Level.parse(levelString);
        final IDebugContext debug;
        if (DEBUG) {
            debug = new LoggerDebugContext(logger, Level.Info);
        } else {
            debug = level != null ? new LoggerDebugContext(logger, level) : new NullDebugContext();
        }

        final Tuple2<List<ITermVar>, Set<IConstraint>> vars_constraint = M
                .tuple2(M.listElems(StatixTerms.varTerm()), StatixTerms.constraints(spec.labels()),
                        (t, vs, c) -> ImmutableTuple2.of(vs, c))
                .match(term).orElseThrow(() -> new InterpreterException("Expected constraint."));

        final ISolverResult resultConfig;
        final ISubstitution.Immutable isubst;
        final IUnifier.Immutable unifier;
        //TODO TAICO Determine ID from somewhere for this module
        final ModuleManager manager = new ModuleManager();
        final IModule module = new Module(manager, "G", spec);
        final SolverCoordinator coordinator = new SolverCoordinator();
        final MState state = new MState(manager, coordinator, module, spec);
        final ISubstitution.Transient subst = PersistentSubstitution.Transient.of();
        for(ITermVar var : vars_constraint._1()) {
            final ITermVar nvar = state.freshVar(var.getName());
            subst.put(var, nvar);
            subst.put(nvar, var);
        }
        isubst = subst.freeze();
        final Set<IConstraint> constraints =
                vars_constraint._2().stream().map(c -> c.apply(isubst)).collect(Collectors.toSet());
        
        try {
            resultConfig = coordinator.solve(state, constraints, debug);
        } catch(InterruptedException e) {
            throw new InterpreterException(e);
        }
        
        unifier = state.unifier();

        final List<ITerm> errorList = Lists.newArrayList();
        if(resultConfig.hasErrors()) {
            resultConfig.errors().stream().map(c -> makeMessage("Failed", c, unifier)).forEach(errorList::add);
        }

        final Collection<IConstraint> unsolved = resultConfig.delays().keySet();
        if(!unsolved.isEmpty()) {
            unsolved.stream().map(c -> makeMessage("Unsolved", c, unifier)).forEach(errorList::add);
        }

        List<ITerm> vsubst = Lists.newArrayList();
        for(ITermVar var : vars_constraint._1()) {
            final ITerm key = isubst.apply(var);
            final ITerm value = unifier.findRecursive(key);
            final ITerm varTerm = isubst.apply(value);
            if(!var.equals(varTerm)) {
                vsubst.add(B.newTuple(StatixTerms.explicate(var), StatixTerms.explicate(varTerm)));
            }
        }
        final ITerm solution = B.newList(vsubst);
        final IListTerm errors = B.newList(errorList);
        final IListTerm warnings = B.EMPTY_LIST;
        final IListTerm notes = B.EMPTY_LIST;
        final ITerm resultTerm = B.newTuple(solution, errors, warnings, notes);
        return Optional.of(resultTerm);
    }

    private void reportOverlappingRules(final Spec spec) {
        final ListMultimap<String, IRule> overlappingRules = spec.overlappingRules();
        if(!overlappingRules.isEmpty()) {
            logger.error("+-------------------------+");
            logger.error("| FOUND OVERLAPPING RULES |");
            logger.error("+-------------------------+");
            for(Map.Entry<String, Collection<IRule>> entry : overlappingRules.asMap().entrySet()) {
                logger.error("| Overlapping rules for: {}", entry.getKey());
                for(IRule rule : entry.getValue()) {
                    logger.error("| * {}", rule);
                }
            }
            logger.error("+-------------------------+");
        }
    }

    private ITerm makeMessage(String prefix, IConstraint constraint, IUnifier.Immutable unifier) {
        final ITerm astTerm = findClosestASTTerm(constraint, unifier);
        final StringBuilder message = new StringBuilder();
        message.append(prefix).append(": ").append(constraint.toString(ModuleSolver.shallowTermFormatter(unifier)))
                .append("\n");
        formatTrace(constraint, unifier, message);
        return B.newTuple(makeOriginTerm(astTerm), B.newString(message.toString()));
    }

    private ITerm findClosestASTTerm(IConstraint constraint, IUnifier unifier) {
        return Iterables2.stream(constraint.terms()).map(unifier::findTerm).filter(t -> TermIndex.get(t).isPresent())
                .findAny().orElseGet(() -> {
                    return constraint.cause().map(cause -> findClosestASTTerm(cause, unifier)).orElse(B.EMPTY_TUPLE);
                });
    }

    private ITerm makeOriginTerm(ITerm term) {
        return B.EMPTY_TUPLE.withAttachments(term.getAttachments());
    }

    private static void formatTrace(@Nullable IConstraint constraint, IUnifier.Immutable unifier, StringBuilder sb) {
        while(constraint != null) {
            sb.append("<br>");
            sb.append("&gt;&nbsp;");
            sb.append(constraint.toString(ModuleSolver.shallowTermFormatter(unifier)));
            constraint = constraint.cause().orElse(null);
        }
    }

}