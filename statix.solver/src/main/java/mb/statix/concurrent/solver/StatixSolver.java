package mb.statix.concurrent.solver;

import static com.google.common.collect.Streams.stream;
import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermMatch.M;
import static mb.statix.constraints.Constraints.disjoin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.metaborg.util.functions.CheckedAction0;
import org.metaborg.util.functions.Function0;
import org.metaborg.util.log.Level;
import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;
import org.metaborg.util.task.NullProgress;
import org.metaborg.util.unit.Unit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;

import io.usethesource.capsule.Set;
import io.usethesource.capsule.util.stream.CapsuleCollectors;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.stratego.TermIndex;
import mb.nabl2.terms.stratego.TermOrigin;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.substitution.PersistentSubstitution;
import mb.nabl2.terms.unification.OccursException;
import mb.nabl2.terms.unification.RigidException;
import mb.nabl2.terms.unification.u.IUnifier;
import mb.nabl2.terms.unification.ud.Diseq;
import mb.nabl2.terms.unification.ud.IUniDisunifier;
import mb.nabl2.util.CapsuleUtil;
import mb.nabl2.util.Tuple2;
import mb.statix.concurrent.actors.futures.CompletableFuture;
import mb.statix.concurrent.actors.futures.IFuture;
import mb.statix.concurrent.p_raffrayi.DeadlockException;
import mb.statix.concurrent.p_raffrayi.ITypeCheckerContext;
import mb.statix.concurrent.p_raffrayi.impl.RegExpLabelWF;
import mb.statix.concurrent.p_raffrayi.impl.RelationLabelOrder;
import mb.statix.concurrent.p_raffrayi.nameresolution.DataLeq;
import mb.statix.concurrent.p_raffrayi.nameresolution.DataLeqInternal;
import mb.statix.concurrent.p_raffrayi.nameresolution.DataWf;
import mb.statix.concurrent.p_raffrayi.nameresolution.DataWfInternal;
import mb.statix.concurrent.p_raffrayi.nameresolution.LabelOrder;
import mb.statix.concurrent.p_raffrayi.nameresolution.LabelWF;
import mb.statix.concurrent.util.VarIndexedCollection;
import mb.statix.constraints.CArith;
import mb.statix.constraints.CAstId;
import mb.statix.constraints.CAstProperty;
import mb.statix.constraints.CConj;
import mb.statix.constraints.CEqual;
import mb.statix.constraints.CExists;
import mb.statix.constraints.CFalse;
import mb.statix.constraints.CInequal;
import mb.statix.constraints.CNew;
import mb.statix.constraints.CResolveQuery;
import mb.statix.constraints.CTellEdge;
import mb.statix.constraints.CTrue;
import mb.statix.constraints.CTry;
import mb.statix.constraints.CUser;
import mb.statix.constraints.messages.IMessage;
import mb.statix.constraints.messages.MessageUtil;
import mb.statix.scopegraph.path.IResolutionPath;
import mb.statix.scopegraph.reference.EdgeOrData;
import mb.statix.scopegraph.terms.AScope;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.solver.CriticalEdge;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IConstraintStore;
import mb.statix.solver.IState;
import mb.statix.solver.ITermProperty;
import mb.statix.solver.ITermProperty.Multiplicity;
import mb.statix.solver.completeness.Completeness;
import mb.statix.solver.completeness.CompletenessUtil;
import mb.statix.solver.completeness.ICompleteness;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.log.LazyDebugContext;
import mb.statix.solver.log.NullDebugContext;
import mb.statix.solver.persistent.BagTermProperty;
import mb.statix.solver.persistent.SingletonTermProperty;
import mb.statix.solver.persistent.Solver;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.solver.query.QueryFilter;
import mb.statix.solver.query.QueryMin;
import mb.statix.solver.query.ResolutionDelayException;
import mb.statix.solver.store.BaseConstraintStore;
import mb.statix.spec.ApplyMode;
import mb.statix.spec.ApplyResult;
import mb.statix.spec.Rule;
import mb.statix.spec.RuleUtil;
import mb.statix.spec.Spec;
import mb.statix.spoofax.StatixTerms;

public class StatixSolver {

    private static final ImmutableSet<ITermVar> NO_UPDATED_VARS = ImmutableSet.of();
    private static final ImmutableList<IConstraint> NO_NEW_CONSTRAINTS = ImmutableList.of();
    private static final mb.statix.solver.completeness.Completeness.Immutable NO_NEW_CRITICAL_EDGES =
            Completeness.Immutable.of();
    private static final ImmutableMap<ITermVar, ITermVar> NO_EXISTENTIALS = ImmutableMap.of();

    private static final int MAX_DEPTH = 32;
    private static final boolean INCREMENTAL_CRITICAL_EDGES = true;

    private final Spec spec;
    private final IConstraintStore constraints;
    private final IDebugContext debug;
    private final IProgress progress;
    private final ICancel cancel;
    private final ITypeCheckerContext<Scope, ITerm, ITerm, SolverResult> scopeGraph;

    private IState.Immutable state;
    private ICompleteness.Immutable completeness;
    private Map<ITermVar, ITermVar> existentials = null;
    private final List<ITermVar> updatedVars = Lists.newArrayList();
    private final Map<IConstraint, IMessage> failed = Maps.newHashMap();

    private final AtomicInteger pendingResults = new AtomicInteger(0);
    private final AtomicInteger ephemeralActiveConstraints = new AtomicInteger(0);
    private final CompletableFuture<SolverResult> result;

    public StatixSolver(IConstraint constraint, Spec spec, IState.Immutable state, ICompleteness.Immutable completeness,
            IDebugContext debug, IProgress progress, ICancel cancel,
            ITypeCheckerContext<Scope, ITerm, ITerm, SolverResult> scopeGraph) {
        if(INCREMENTAL_CRITICAL_EDGES && !spec.hasPrecomputedCriticalEdges()) {
            debug.warn("Leaving precomputing critical edges to solver may result in duplicate work.");
            this.spec = spec.precomputeCriticalEdges();
        } else {
            this.spec = spec;
        }
        this.scopeGraph = scopeGraph;
        this.debug = debug;
        this.state = state;
        this.constraints = new BaseConstraintStore(debug);
        final ICompleteness.Transient _completeness = completeness.melt();
        if(INCREMENTAL_CRITICAL_EDGES) {
            final Tuple2<IConstraint, ICompleteness.Immutable> initialConstraintAndCriticalEdges =
                    CompletenessUtil.precomputeCriticalEdges(constraint, spec.scopeExtensions());
            this.constraints.add(initialConstraintAndCriticalEdges._1());
            _completeness.addAll(initialConstraintAndCriticalEdges._2(), this.state.unifier());
        } else {
            constraints.add(constraint);
            _completeness.add(constraint, spec, state.unifier());
        }
        this.completeness = _completeness.freeze();
        this.result = new CompletableFuture<>();
        this.progress = progress;
        this.cancel = cancel;
    }

    ///////////////////////////////////////////////////////////////////////////
    // driver
    ///////////////////////////////////////////////////////////////////////////

    public IFuture<SolverResult> solve(Scope root) {
        try {
            scopeGraph.initRoot(root, getOpenEdges(root), false);
            fixedpoint();
        } catch(Throwable e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    public IFuture<SolverResult> entail() {
        try {
            fixedpoint();
        } catch(Throwable e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private <R> void solveK(K<R> k, R r, Throwable ex) {
        debug.debug("Solving continuation");
        try {
            k.k(r, ex, MAX_DEPTH);
            fixedpoint();
        } catch(Throwable e) {
            result.completeExceptionally(e);
        }
        debug.debug("Solved continuation");
    }

    // It can happen that fixedpoint is called in the context of a running fixedpoint.
    // This can happen when a continuation is not triggered by a remote message, but
    // directly completed (e.g., by a try). The solveK invocation will call fixedpoint
    // again. To ensure we do not complete too early, it is necessary to track the number
    // of unsolved constraints in the current execution state (because of the direct
    // recursion of k), and only complete when there are no left. This is what the
    // ehpemeralActiveConstraints counter does.
    private void fixedpoint() throws InterruptedException {
        debug.debug("Solving constraints");

        IConstraint constraint;
        while((constraint = constraints.remove()) != null) {
            ephemeralActiveConstraints.incrementAndGet();
            k(constraint, MAX_DEPTH);
        }

        // invariant: there should be no remaining active constraints
        if(constraints.activeSize() > 0) {
            debug.warn("Fixed point finished with remaining constraints");
            throw new IllegalStateException(
                    "Expected no remaining active constraints, but got " + constraints.activeSize());
        }

        debug.debug("Has ephermeral: {}, pending: {}, done: {}", ephemeralActiveConstraints.get(), pendingResults.get(),
                result.isDone());
        if(ephemeralActiveConstraints.get() == 0 && pendingResults.get() == 0 && !result.isDone()) {
            debug.debug("Finished.");
            result.complete(finishSolve());
        } else {
            debug.debug("Not finished.");
        }
    }

    private SolverResult finishSolve() throws InterruptedException {
        final Map<IConstraint, Delay> delayed = constraints.delayed();
        debug.debug("Solved constraints with {} failed and {} remaining constraint(s).", failed.size(),
                constraints.delayedSize());
        if(debug.isEnabled(Level.Debug)) {
            for(Entry<IConstraint, Delay> entry : delayed.entrySet()) {
                debug.debug(" * {} on {}", entry.getKey().toString(state.unifier()::toString), entry.getValue());
                removeCompleteness(entry.getKey());
            }
        }

        final Map<ITermVar, ITermVar> existentials = Optional.ofNullable(this.existentials).orElse(ImmutableMap.of());
        final java.util.Set<CriticalEdge> removedEdges = ImmutableSet.of();
        final ICompleteness.Immutable completeness = Completeness.Immutable.of();
        final SolverResult result =
                SolverResult.of(state, failed, delayed, existentials, updatedVars, removedEdges, completeness);
        return result;
    }

    ///////////////////////////////////////////////////////////////////////////
    // success/failure signals
    ///////////////////////////////////////////////////////////////////////////

    private Unit success(IConstraint constraint, IState.Immutable newState, Collection<ITermVar> updatedVars,
            Collection<IConstraint> newConstraints, ICompleteness.Immutable newCriticalEdges,
            Map<ITermVar, ITermVar> existentials, int fuel) throws InterruptedException {
        state = newState;

        final IDebugContext subDebug = debug.subContext();
        if(this.existentials == null) {
            this.existentials = existentials;
        }
        final IUniDisunifier.Immutable unifier = state.unifier();

        // updates from unified variables
        if(!updatedVars.isEmpty()) {
            final ICompleteness.Transient _completeness = completeness.melt();
            _completeness.updateAll(updatedVars, unifier);
            this.completeness = _completeness.freeze();
            constraints.activateFromVars(updatedVars, debug);
            this.updatedVars.addAll(updatedVars);
        }

        // add new constraints
        if(!newConstraints.isEmpty()) {
            // no constraints::addAll, instead recurse in tail position
            final ICompleteness.Transient _completeness = completeness.melt();
            if(INCREMENTAL_CRITICAL_EDGES) {
                _completeness.addAll(newCriticalEdges, unifier); // must come before ICompleteness::remove
            } else {
                _completeness.addAll(newConstraints, spec, unifier); // must come before ICompleteness::remove
            }
            this.completeness = _completeness.freeze();
            if(subDebug.isEnabled(Level.Debug) && !newConstraints.isEmpty()) {
                subDebug.debug("Simplified to:");
                for(IConstraint newConstraint : newConstraints) {
                    subDebug.debug(" * {}", Solver.toString(newConstraint, unifier));
                }
            }
            ephemeralActiveConstraints.addAndGet(newConstraints.size());
        }

        removeCompleteness(constraint);
        ephemeralActiveConstraints.decrementAndGet();

        // do this after the state has been completely updated
        if(!updatedVars.isEmpty()) {
            releaseDelayedActions(updatedVars);
        }

        // continue on new constraints
        for(IConstraint newConstraint : newConstraints) {
            k(newConstraint, fuel - 1);
        }

        return Unit.unit;
    }

    private Unit delay(IConstraint constraint, IState.Immutable newState, Delay delay, int fuel)
            throws InterruptedException {
        ephemeralActiveConstraints.decrementAndGet();

        if(!delay.criticalEdges().isEmpty()) {
            debug.error("FIXME: query failed on critical edges {}: {}", delay.criticalEdges(),
                    constraint.toString(state.unifier()::toString));
            return fail(constraint);
        }

        final Set.Immutable<ITermVar> vars = delay.vars().stream().flatMap(v -> state.unifier().getVars(v).stream())
                .collect(CapsuleCollectors.toSet());
        if(vars.isEmpty()) {
            debug.error("query delayed on no vars, rescheduling: {}", delay.criticalEdges(),
                    constraint.toString(state.unifier()::toString));
            return fail(constraint);
        }

        if(debug.isEnabled(Level.Debug)) {
            debug.debug("query delayed on vars {}: {}", vars, constraint.toString(state.unifier()::toString));
        }

        final IDebugContext subDebug = debug.subContext();
        constraints.delay(constraint, delay);
        if(subDebug.isEnabled(Level.Debug)) {
            subDebug.debug("Delayed: {}", Solver.toString(constraint, state.unifier()));
        }

        return Unit.unit;
    }

    private <R> Unit future(IConstraint c, IState.Immutable newState, IFuture<R> future, K<? super R> k, int fuel)
            throws InterruptedException {
        pendingResults.incrementAndGet();
        future.handle((r, ex) -> {
            pendingResults.decrementAndGet();
            solveK(k, r, ex);
            return Unit.unit;
        });
        return Unit.unit;
    }

    private Unit fail(IConstraint constraint) throws InterruptedException {
        failed.put(constraint, MessageUtil.findClosestMessage(constraint));
        removeCompleteness(constraint);
        ephemeralActiveConstraints.decrementAndGet();
        return Unit.unit;
    }

    private void removeCompleteness(IConstraint constraint) throws InterruptedException {
        final Set.Immutable<CriticalEdge> removedEdges;
        final ICompleteness.Transient _completeness = completeness.melt();
        if(INCREMENTAL_CRITICAL_EDGES) {
            if(!constraint.ownCriticalEdges().isPresent()) {
                throw new IllegalArgumentException("Solver only accepts constraints with pre-computed critical edges.");
            }
            removedEdges = _completeness.removeAll(constraint.ownCriticalEdges().get(), state.unifier());
        } else {
            removedEdges = _completeness.remove(constraint, spec, state.unifier());
        }
        for(CriticalEdge criticalEdge : removedEdges) {
            closeEdge(criticalEdge);
        }
        this.completeness = _completeness.freeze();
    }

    private Unit queue(IConstraint constraint) {
        ephemeralActiveConstraints.decrementAndGet();
        constraints.add(constraint);
        return Unit.unit;
    }

    ///////////////////////////////////////////////////////////////////////////
    // k
    ///////////////////////////////////////////////////////////////////////////

    private Unit k(IConstraint constraint, int fuel) throws InterruptedException {
        // stop if thread is interrupted
        if(cancel.cancelled()) {
            throw new InterruptedException();
        }

        // stop recursion if we run out of fuel
        if(fuel <= 0) {
            return queue(constraint);
        }

        if(debug.isEnabled(Level.Debug)) {
            debug.debug("Solving {}", constraint.toString(Solver.shallowTermFormatter(state.unifier())));
        }

        // solve
        return constraint.matchOrThrow(new IConstraint.CheckedCases<Unit, InterruptedException>() {

            @Override public Unit caseArith(CArith c) throws InterruptedException {
                final IUniDisunifier unifier = state.unifier();
                final Optional<ITerm> term1 = c.expr1().isTerm();
                final Optional<ITerm> term2 = c.expr2().isTerm();
                try {
                    if(c.op().isEquals() && term1.isPresent()) {
                        int i2 = c.expr2().eval(unifier);
                        final IConstraint eq = new CEqual(term1.get(), B.newInt(i2), c);
                        return success(c, state, NO_UPDATED_VARS, ImmutableList.of(eq), NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    } else if(c.op().isEquals() && term2.isPresent()) {
                        int i1 = c.expr1().eval(unifier);
                        final IConstraint eq = new CEqual(B.newInt(i1), term2.get(), c);
                        return success(c, state, NO_UPDATED_VARS, ImmutableList.of(eq), NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    } else {
                        int i1 = c.expr1().eval(unifier);
                        int i2 = c.expr2().eval(unifier);
                        if(c.op().test(i1, i2)) {
                            return success(c, state, NO_UPDATED_VARS, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES,
                                    NO_EXISTENTIALS, fuel);
                        } else {
                            return fail(c);
                        }
                    }
                } catch(Delay d) {
                    return delay(c, state, d, fuel);
                }
            }

            @Override public Unit caseConj(CConj c) throws InterruptedException {
                return success(c, state, NO_UPDATED_VARS, disjoin(c), NO_NEW_CRITICAL_EDGES, NO_EXISTENTIALS, fuel);
            }

            @Override public Unit caseEqual(CEqual c) throws InterruptedException {
                final ITerm term1 = c.term1();
                final ITerm term2 = c.term2();
                IUniDisunifier.Immutable unifier = state.unifier();
                try {
                    final IUniDisunifier.Result<IUnifier.Immutable> result;
                    if((result = unifier.unify(term1, term2, v -> isRigid(v, state)).orElse(null)) != null) {
                        if(debug.isEnabled(Level.Debug)) {
                            debug.debug("Unification succeeded: {}", result.result());
                        }
                        final IState.Immutable newState = state.withUnifier(result.unifier());
                        final Set<ITermVar> updatedVars = result.result().domainSet();
                        return success(c, newState, updatedVars, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    } else {
                        if(debug.isEnabled(Level.Debug)) {
                            debug.debug("Unification failed: {} != {}", unifier.toString(term1),
                                    unifier.toString(term2));
                        }
                        return fail(c);
                    }
                } catch(OccursException e) {
                    if(debug.isEnabled(Level.Debug)) {
                        debug.debug("Unification failed: {} != {}", unifier.toString(term1), unifier.toString(term2));
                    }
                    return fail(c);
                } catch(RigidException e) {
                    return delay(c, state, Delay.ofVars(e.vars()), fuel);
                }
            }

            @Override public Unit caseExists(CExists c) throws InterruptedException {
                final ImmutableMap.Builder<ITermVar, ITermVar> existentialsBuilder = ImmutableMap.builder();
                IState.Immutable newState = state;
                for(ITermVar var : c.vars()) {
                    final Tuple2<ITermVar, IState.Immutable> varAndState = newState.freshVar(var);
                    final ITermVar freshVar = varAndState._1();
                    newState = varAndState._2();
                    existentialsBuilder.put(var, freshVar);
                }
                final Map<ITermVar, ITermVar> existentials = existentialsBuilder.build();
                final ISubstitution.Immutable subst = PersistentSubstitution.Immutable.of(existentials);
                final IConstraint newConstraint = c.constraint().apply(subst).withCause(c.cause().orElse(null));
                if(INCREMENTAL_CRITICAL_EDGES && !c.bodyCriticalEdges().isPresent()) {
                    throw new IllegalArgumentException(
                            "Solver only accepts constraints with pre-computed critical edges.");
                }
                final ICompleteness.Immutable newCriticalEdges =
                        c.bodyCriticalEdges().orElse(NO_NEW_CRITICAL_EDGES).apply(subst);
                return success(c, newState, NO_UPDATED_VARS, disjoin(newConstraint), newCriticalEdges, existentials,
                        fuel);
            }

            @Override public Unit caseFalse(CFalse c) throws InterruptedException {
                return fail(c);
            }

            @Override public Unit caseInequal(CInequal c) throws InterruptedException {
                final ITerm term1 = c.term1();
                final ITerm term2 = c.term2();
                final IUniDisunifier.Immutable unifier = state.unifier();
                try {
                    final IUniDisunifier.Result<Optional<Diseq>> result;
                    if((result = unifier.disunify(c.universals(), term1, term2, v -> isRigid(v, state))
                            .orElse(null)) != null) {
                        if(debug.isEnabled(Level.Debug)) {
                            debug.debug("Disunification succeeded: {}", result);
                        }
                        final IState.Immutable newState = state.withUnifier(result.unifier());
                        final Set<ITermVar> updatedVars =
                                result.result().<Set<ITermVar>>map(Diseq::domainSet).orElse(CapsuleUtil.immutableSet());
                        return success(c, newState, updatedVars, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    } else {
                        if(debug.isEnabled(Level.Debug)) {
                            debug.debug("Disunification failed");
                        }
                        return fail(c);
                    }
                } catch(RigidException e) {
                    return delay(c, state, Delay.ofVars(e.vars()), fuel);
                }
            }

            @Override public Unit caseNew(CNew c) throws InterruptedException {
                final ITerm scopeTerm = c.scopeTerm();
                final ITerm datumTerm = c.datumTerm();
                final String name = M.var(ITermVar::getName).match(scopeTerm).orElse("s");
                final Set<ITerm> labels = getOpenEdges(scopeTerm);

                final Scope scope = scopeGraph.freshScope(name, labels, true, false);
                scopeGraph.setDatum(scope, datumTerm);
                final IConstraint eq = new CEqual(scopeTerm, scope, c);
                return success(c, state, NO_UPDATED_VARS, ImmutableList.of(eq), NO_NEW_CRITICAL_EDGES, NO_EXISTENTIALS,
                        fuel);
            }

            @Override public Unit caseResolveQuery(CResolveQuery c) throws InterruptedException {
                final ITerm scopeTerm = c.scopeTerm();
                final QueryFilter filter = c.filter();
                final QueryMin min = c.min();
                final ITerm resultTerm = c.resultTerm();

                final IUniDisunifier unifier = state.unifier();
                // @formatter:off
                final Set.Immutable<ITermVar> freeVars = Streams.concat(
                        unifier.getVars(scopeTerm).stream(),
                        RuleUtil.freeVars(filter.getDataWF()).stream().flatMap(v -> unifier.getVars(v).stream()),
                        RuleUtil.freeVars(min.getDataEquiv()).stream().flatMap(v -> unifier.getVars(v).stream())
                ).collect(CapsuleCollectors.toSet());
                // @formatter:on
                if(!freeVars.isEmpty()) {
                    return delay(c, state, Delay.ofVars(freeVars), fuel);
                }

                final Scope scope = AScope.matcher().match(scopeTerm, unifier).orElseThrow(
                        () -> new IllegalArgumentException("Expected scope, got " + unifier.toString(scopeTerm)));

                final LabelWF<ITerm> labelWF = new RegExpLabelWF(filter.getLabelWF());
                final LabelOrder<ITerm> labelOrder = new RelationLabelOrder(min.getLabelOrder());
                final DataWf<ITerm> dataWF = new ConstraintDataWF(spec, state, filter.getDataWF());
                final DataLeq<ITerm> dataEquiv = new ConstraintDataEquiv(spec, state, min.getDataEquiv());
                final DataWfInternal<ITerm> dataWFInternal = new ConstraintDataWFInternal(filter.getDataWF());
                final DataLeqInternal<ITerm> dataEquivInternal = new ConstraintDataEquivInternal(min.getDataEquiv());

                final IFuture<? extends java.util.Set<IResolutionPath<Scope, ITerm, ITerm>>> future = scopeGraph
                        .query(scope, labelWF, labelOrder, dataWF, dataEquiv, dataWFInternal, dataEquivInternal);

                final K<java.util.Set<IResolutionPath<Scope, ITerm, ITerm>>> k = (paths, ex, fuel) -> {
                    if(ex != null) {
                        // pattern matching for the brave and stupid
                        try {
                            throw ex;
                        } catch(ResolutionDelayException rde) {
                            if(debug.isEnabled(Level.Debug)) {
                                debug.debug("delayed query (unsupported) {}", rde,
                                        c.toString(state.unifier()::toString));
                            }
                            return fail(c);
                        } catch(DeadlockException dle) {
                            if(debug.isEnabled(Level.Debug)) {
                                debug.debug("deadlocked query (spec error) {}", c.toString(state.unifier()::toString));
                            }
                            return fail(c);
                        } catch(Throwable t) {
                            debug.error("failed query {}", t, c.toString(state.unifier()::toString));
                            return fail(c);
                        }
                    } else {
                        final List<ITerm> pathTerms =
                                paths.stream().map(p -> StatixTerms.explicate(p, spec.dataLabels()))
                                        .collect(ImmutableList.toImmutableList());
                        final IConstraint C = new CEqual(resultTerm, B.newList(pathTerms), c);
                        return success(c, state, NO_UPDATED_VARS, ImmutableList.of(C), NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    }
                };
                return future(c, state, future, k, fuel);
            }

            @Override public Unit caseTellEdge(CTellEdge c) throws InterruptedException {
                final ITerm sourceTerm = c.sourceTerm();
                final ITerm label = c.label();
                final ITerm targetTerm = c.targetTerm();
                final IUniDisunifier unifier = state.unifier();
                if(!unifier.isGround(sourceTerm)) {
                    return delay(c, state, Delay.ofVars(unifier.getVars(sourceTerm)), fuel);
                }
                if(!unifier.isGround(targetTerm)) {
                    return delay(c, state, Delay.ofVars(unifier.getVars(targetTerm)), fuel);
                }
                final Scope source =
                        AScope.matcher().match(sourceTerm, unifier).orElseThrow(() -> new IllegalArgumentException(
                                "Expected source scope, got " + unifier.toString(sourceTerm)));
                final Scope target =
                        AScope.matcher().match(targetTerm, unifier).orElseThrow(() -> new IllegalArgumentException(
                                "Expected target scope, got " + unifier.toString(targetTerm)));
                scopeGraph.addEdge(source, label, target);
                return success(c, state, NO_UPDATED_VARS, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES, NO_EXISTENTIALS,
                        fuel);
            }

            @Override public Unit caseTermId(CAstId c) throws InterruptedException {
                final ITerm term = c.astTerm();
                final ITerm idTerm = c.idTerm();

                final IUniDisunifier unifier = state.unifier();
                if(!(unifier.isGround(term))) {
                    return delay(c, state, Delay.ofVars(unifier.getVars(term)), fuel);
                }
                final CEqual eq;
                final Optional<Scope> maybeScope = AScope.matcher().match(term, unifier);
                if(maybeScope.isPresent()) {
                    final AScope scope = maybeScope.get();
                    eq = new CEqual(idTerm, scope);
                    return success(c, state, NO_UPDATED_VARS, ImmutableList.of(eq), NO_NEW_CRITICAL_EDGES,
                            NO_EXISTENTIALS, fuel);
                } else {
                    final Optional<TermIndex> maybeIndex = TermIndex.get(unifier.findTerm(term));
                    if(maybeIndex.isPresent()) {
                        final ITerm indexTerm = TermOrigin.copy(term, maybeIndex.get());
                        eq = new CEqual(idTerm, indexTerm);
                        return success(c, state, NO_UPDATED_VARS, ImmutableList.of(eq), NO_NEW_CRITICAL_EDGES,
                                NO_EXISTENTIALS, fuel);
                    } else {
                        return fail(c);
                    }
                }
            }

            @Override public Unit caseTermProperty(CAstProperty c) throws InterruptedException {
                final ITerm idTerm = c.idTerm();
                final ITerm prop = c.property();
                final ITerm value = c.value();

                final IUniDisunifier unifier = state.unifier();
                if(!(unifier.isGround(idTerm))) {
                    return delay(c, state, Delay.ofVars(unifier.getVars(idTerm)), fuel);
                }
                final Optional<TermIndex> maybeIndex = TermIndex.matcher().match(idTerm, unifier);
                if(maybeIndex.isPresent()) {
                    final TermIndex index = maybeIndex.get();
                    final Tuple2<TermIndex, ITerm> key = Tuple2.of(index, prop);
                    ITermProperty property;
                    switch(c.op()) {
                        case ADD: {
                            property = state.termProperties().getOrDefault(key, BagTermProperty.of());
                            if(!property.multiplicity().equals(Multiplicity.BAG)) {
                                return fail(c);
                            }
                            property = property.addValue(value);
                            break;
                        }
                        case SET: {
                            if(state.termProperties().containsKey(key)) {
                                return fail(c);
                            }
                            property = SingletonTermProperty.of(value);
                            break;
                        }
                        default:
                            throw new IllegalStateException("Unknown op " + c.op());
                    }
                    final IState.Immutable newState =
                            state.withTermProperties(state.termProperties().__put(key, property));
                    return success(c, newState, NO_UPDATED_VARS, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES,
                            NO_EXISTENTIALS, fuel);
                } else {
                    return fail(c);
                }
            }

            @Override public Unit caseTrue(CTrue c) throws InterruptedException {
                return success(c, state, NO_UPDATED_VARS, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES, NO_EXISTENTIALS,
                        fuel);
            }

            @Override public Unit caseTry(CTry c) throws InterruptedException {
                final IDebugContext subDebug = debug.subContext();
                final ITypeCheckerContext<Scope, ITerm, ITerm, SolverResult> subContext = scopeGraph.subContext("try");
                final IState.Immutable subState = state.subState().withResource(subContext.id());
                final StatixSolver subSolver = new StatixSolver(c.constraint(), spec, subState, completeness, subDebug,
                        progress, cancel, subContext);
                final IFuture<SolverResult> subResult = subSolver.entail();
                final K<SolverResult> k = (r, ex, fuel) -> {
                    if(ex != null) {
                        debug.error("try {} failed", ex, c.toString(state.unifier()::toString));
                        return fail(c);
                    } else {
                        try {
                            // check entailment w.r.t. the initial substate, not the current state: otherwise,
                            // some variables may be treated as external while they are not
                            if(Solver.entailed(subState, r, subDebug)) {
                                if(debug.isEnabled(Level.Debug)) {
                                    debug.debug("constraint {} entailed", c.toString(state.unifier()::toString));
                                }
                                return success(c, state, NO_UPDATED_VARS, NO_NEW_CONSTRAINTS, NO_NEW_CRITICAL_EDGES,
                                        NO_EXISTENTIALS, fuel);
                            } else {
                                if(debug.isEnabled(Level.Debug)) {
                                    debug.debug("constraint {} not entailed", c.toString(state.unifier()::toString));
                                }
                                return fail(c);

                            }
                        } catch(Delay delay) {
                            return delay(c, state, delay, fuel);
                        }
                    }
                };
                return future(c, state, subResult, k, fuel);
            }

            @Override public Unit caseUser(CUser c) throws InterruptedException {
                final String name = c.name();
                final List<ITerm> args = c.args();

                final LazyDebugContext proxyDebug = new LazyDebugContext(debug);

                final List<Rule> rules = spec.rules().getRules(name);
                final List<Tuple2<Rule, ApplyResult>> results =
                        RuleUtil.applyOrderedAll(state.unifier(), rules, args, c, ApplyMode.RELAXED);
                if(results.isEmpty()) {
                    debug.debug("No rule applies");
                    return fail(c);
                } else if(results.size() == 1) {
                    final ApplyResult applyResult = results.get(0)._2();
                    proxyDebug.debug("Rule accepted");
                    proxyDebug.commit();
                    if(INCREMENTAL_CRITICAL_EDGES && applyResult.criticalEdges() == null) {
                        throw new IllegalArgumentException(
                                "Solver only accepts specs with pre-computed critical edges.");
                    }
                    final ICompleteness.Immutable newCriticalEdges =
                            Optional.ofNullable(applyResult.criticalEdges()).orElse(NO_NEW_CRITICAL_EDGES);
                    return success(c, state, NO_UPDATED_VARS, disjoin(applyResult.body()), newCriticalEdges,
                            NO_EXISTENTIALS, fuel);
                } else {
                    final Set<ITermVar> stuckVars = results.stream().flatMap(r -> Streams.stream(r._2().guard()))
                            .flatMap(g -> g.domainSet().stream()).collect(CapsuleCollectors.toSet());
                    proxyDebug.debug("Rule delayed (multiple conditional matches)");
                    return delay(c, state, Delay.ofVars(stuckVars), fuel);
                }
            }

        });

    }

    ///////////////////////////////////////////////////////////////////////////
    // entailment
    ///////////////////////////////////////////////////////////////////////////

    private IFuture<Boolean> entails(IConstraint constraint) {
        final IDebugContext subDebug = debug.subContext();
        final ITypeCheckerContext<Scope, ITerm, ITerm, SolverResult> subContext = scopeGraph.subContext("try");
        return absorbDelays(() -> {
            final IState.Immutable subState = state.subState().withResource(subContext.id());
            final StatixSolver subSolver =
                    new StatixSolver(constraint, spec, subState, completeness, subDebug, progress, cancel, subContext);
            return subSolver.entail().thenCompose(r -> {
                final boolean result;
                // check entailment w.r.t. the initial substate, not the current state: otherwise,
                // some variables may be treated as external while they are not
                if(Solver.entailed(subState, r, subDebug)) {
                    if(debug.isEnabled(Level.Debug)) {
                        debug.debug("constraint {} entailed", constraint.toString(state.unifier()::toString));
                    }
                    result = true;
                } else {
                    if(debug.isEnabled(Level.Debug)) {
                        debug.debug("constraint {} not entailed", constraint.toString(state.unifier()::toString));
                    }
                    result = false;
                }
                return CompletableFuture.completedFuture(result);
            });
        });
    }

    private <T> IFuture<T> absorbDelays(Function0<IFuture<T>> f) {
        return f.apply().compose((r, ex) -> {
            if(ex != null) {
                try {
                    throw ex;
                } catch(Delay delay) {
                    if(!delay.criticalEdges().isEmpty()) {
                        debug.error("unsupported delay with critical edges {}", delay);
                        throw new IllegalStateException("unsupported delay with critical edges");
                    }
                    if(delay.vars().isEmpty()) {
                        debug.error("unsupported delay without variables {}", delay);
                        throw new IllegalStateException("unsupported delay without variables");
                    }
                    final CompletableFuture<T> result = new CompletableFuture<>();
                    try {
                        delayAction(() -> {
                            absorbDelays(f).whenComplete(result::complete);
                        }, delay.vars());
                    } catch(InterruptedException ie) {
                        result.completeExceptionally(ie);
                    }
                    return result;
                } catch(Throwable t) {
                    return CompletableFuture.completedExceptionally(t);
                }
            } else {
                return CompletableFuture.completedFuture(r);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Open edges & delayed closes
    ///////////////////////////////////////////////////////////////////////////

    private Set.Transient<CriticalEdge> delayedCloses = CapsuleUtil.transientSet();

    private Set.Immutable<ITerm> getOpenEdges(ITerm varOrScope) {
        // we must include queued edge closes here, to ensure we registered the open
        // edge when the close is released
        final List<EdgeOrData<ITerm>> openEdges =
                Streams.stream(completeness.get(varOrScope, state.unifier())).collect(Collectors.toList());
        final List<EdgeOrData<ITerm>> queuedEdges = M.var().match(varOrScope)
                .map(var -> delayedCloses.stream().filter(e -> state.unifier().findRecursive(var).equals(e.scope()))
                        .map(e -> e.edgeOrData()))
                .orElse(Stream.<EdgeOrData<ITerm>>empty()).collect(Collectors.toList());
        return stream(Iterables.concat(openEdges, queuedEdges)).<ITerm>flatMap(eod -> {
            return eod.match(() -> Stream.<ITerm>empty(), (l) -> Stream.of(l));
        }).collect(CapsuleCollectors.toSet());
    }

    private void closeEdge(CriticalEdge criticalEdge) throws InterruptedException {
        if(debug.isEnabled(Level.Debug)) {
            debug.debug("client {} close edge {}/{}", this, state.unifier().toString(criticalEdge.scope()),
                    criticalEdge.edgeOrData());
        }
        delayedCloses.__insert(criticalEdge);
        delayAction(() -> {
            delayedCloses.__remove(criticalEdge);
            closeGroundEdge(criticalEdge);
        }, state.unifier().getVars(criticalEdge.scope()));
    }

    private void closeGroundEdge(CriticalEdge criticalEdge) {
        if(debug.isEnabled(Level.Debug)) {
            debug.debug("client {} close edge {}/{}", this, state.unifier().toString(criticalEdge.scope()),
                    criticalEdge.edgeOrData());
        }
        final Scope scope = Scope.matcher().match(criticalEdge.scope(), state.unifier())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Expected scope, got " + state.unifier().toString(criticalEdge.scope())));
        // @formatter:off
        criticalEdge.edgeOrData().match(
            () -> {
                // ignore data labels, they are managed separately
                return Unit.unit;
            },
            label -> {
                scopeGraph.closeEdge(scope, label);
                return Unit.unit;
            }
        );
        // @formatter:on
    }

    ///////////////////////////////////////////////////////////////////////////
    // Delayed actions
    ///////////////////////////////////////////////////////////////////////////

    private final VarIndexedCollection<CheckedAction0<InterruptedException>> delayedActions =
            new VarIndexedCollection<>();

    private void delayAction(CheckedAction0<InterruptedException> action, Iterable<ITermVar> vars)
            throws InterruptedException {
        if(!delayedActions.put(action, vars, state.unifier())) {
            action.apply();
        }
    }

    private void releaseDelayedActions(Iterable<ITermVar> updatedVars) throws InterruptedException {
        for(CheckedAction0<InterruptedException> action : delayedActions.update(updatedVars, state.unifier())) {
            action.apply();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // external data
    ///////////////////////////////////////////////////////////////////////////

    public IFuture<ITerm> getExternalRepresentation(ITerm t) {
        final CompletableFuture<ITerm> f = new CompletableFuture<>();
        try {
            delayAction(() -> {
                f.complete(state.unifier().findRecursive(t));
            }, state.unifier().getVars(t));
        } catch(InterruptedException ex) {
            f.completeExceptionally(ex);
        }
        return f;
    }

    ///////////////////////////////////////////////////////////////////////////
    // data wf & leq
    ///////////////////////////////////////////////////////////////////////////

    private static class ConstraintDataWF implements DataWf<ITerm> {

        private final Spec spec;
        private final IState.Immutable state;
        private final Rule constraint;

        public ConstraintDataWF(Spec spec, IState.Immutable state, Rule constraint) {
            this.spec = spec;
            this.state = state;
            this.constraint = constraint;
        }

        @Override public boolean wf(ITerm datum, ICancel cancel) throws InterruptedException {
            try {
                final ApplyResult result;
                if((result =
                        RuleUtil.apply(state.unifier(), constraint, ImmutableList.of(datum), null, ApplyMode.STRICT)
                                .orElse(null)) == null) {
                    return false;
                }
                return Solver.entails(spec, state, result.body(), (s, l, st) -> true, new NullDebugContext(),
                        new NullProgress(), cancel);
            } catch(Delay e) {
                throw new IllegalStateException("Unexpected delay.", e);
            }
        }

        @Override public String toString() {
            return constraint.toString(state.unifier()::toString);
        }

    }

    private class ConstraintDataWFInternal implements DataWfInternal<ITerm> {

        private final Rule constraint;

        public ConstraintDataWFInternal(Rule constraint) {
            this.constraint = constraint;
        }

        @Override public IFuture<Boolean> wf(ITerm datum, ICancel cancel) throws InterruptedException {
            return absorbDelays(() -> {
                try {
                    final ApplyResult result;
                    if((result =
                            RuleUtil.apply(state.unifier(), constraint, ImmutableList.of(datum), null, ApplyMode.STRICT)
                                    .orElse(null)) == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return entails(result.body());
                } catch(Delay delay) {
                    return CompletableFuture.completedExceptionally(delay);
                }
            });
        }

        @Override public String toString() {
            return constraint.toString(state.unifier()::toString);
        }

    }

    private static class ConstraintDataEquiv implements DataLeq<ITerm> {

        private final Spec spec;
        private final IState.Immutable state;
        private final Rule constraint;

        public ConstraintDataEquiv(Spec spec, IState.Immutable state, Rule constraint) {
            this.spec = spec;
            this.state = state;
            this.constraint = constraint;
        }

        @Override public boolean leq(ITerm datum1, ITerm datum2, ICancel cancel) throws InterruptedException {
            try {
                final ApplyResult result;
                if((result = RuleUtil
                        .apply(state.unifier(), constraint, ImmutableList.of(datum1, datum2), null, ApplyMode.STRICT)
                        .orElse(null)) == null) {
                    return false;
                }
                return Solver.entails(spec, state, result.body(), (s, l, st) -> true, new NullDebugContext(),
                        new NullProgress(), cancel);
            } catch(Delay e) {
                throw new IllegalStateException("Unexpected delay.", e);
            }
        }

        @Override public String toString() {
            return constraint.toString(state.unifier()::toString);
        }

    }

    private class ConstraintDataEquivInternal implements DataLeqInternal<ITerm> {

        private final Rule constraint;

        public ConstraintDataEquivInternal(Rule constraint) {
            this.constraint = constraint;
        }

        @Override public IFuture<Boolean> leq(ITerm datum1, ITerm datum2, ICancel cancel) throws InterruptedException {
            return absorbDelays(() -> {
                try {
                    final ApplyResult result;
                    if((result = RuleUtil.apply(state.unifier(), constraint, ImmutableList.of(datum1, datum2), null,
                            ApplyMode.STRICT).orElse(null)) == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return entails(result.body());
                } catch(Delay delay) {
                    return CompletableFuture.completedExceptionally(delay);
                }
            });
        }

        @Override public String toString() {
            return constraint.toString(state.unifier()::toString);
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // rigidness
    ///////////////////////////////////////////////////////////////////////////

    private boolean isRigid(ITermVar var, IState state) {
        return !state.vars().contains(var);
    }

    ///////////////////////////////////////////////////////////////////////////
    // toString
    ///////////////////////////////////////////////////////////////////////////

    @Override public String toString() {
        return "StatixSolver";
    }

    ///////////////////////////////////////////////////////////////////////////
    // K
    ///////////////////////////////////////////////////////////////////////////

    @FunctionalInterface
    private interface K<R> {

        Unit k(R result, Throwable ex, int fuel) throws InterruptedException;

    }

}