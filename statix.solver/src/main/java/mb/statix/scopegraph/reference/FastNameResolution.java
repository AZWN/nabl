package mb.statix.scopegraph.reference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.metaborg.util.functions.Predicate2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import io.usethesource.capsule.Set;
import mb.nabl2.util.ImmutableTuple2;
import mb.nabl2.util.Tuple2;
import mb.statix.scopegraph.INameResolution;
import mb.statix.scopegraph.IScopeGraph;
import mb.statix.scopegraph.path.IResolutionPath;
import mb.statix.scopegraph.path.IScopePath;
import mb.statix.scopegraph.terms.path.Paths;

public class FastNameResolution<S extends D, L, D> implements INameResolution<S, L, D> {

    private final IScopeGraph<S, L, D> scopeGraph;
    private final Set.Immutable<L> labels;
    private final Optional<L> relation;

    private final LabelWF<L> labelWF; // default: true
    private final LabelOrder<L> labelOrder; // default: false
    private final Predicate2<S, L> isEdgeComplete; // default: true

    private final DataWF<D> dataWF; // default: true
    private final DataLeq<D> dataEquiv; // default: false
    private final Predicate2<S, L> isDataComplete; // default: true

    public FastNameResolution(IScopeGraph<S, L, D> scopeGraph, Optional<L> relation, LabelWF<L> labelWF,
            LabelOrder<L> labelOrder, Predicate2<S, L> isEdgeComplete, DataWF<D> dataWF, DataLeq<D> dataEquiv,
            Predicate2<S, L> isDataComplete) {
        super();
        this.scopeGraph = scopeGraph;
        this.labels = Set.Immutable.<L>of().__insertAll(scopeGraph.getLabels()).__insert(scopeGraph.getEndOfPath());
        this.relation = relation;
        this.labelWF = labelWF;
        this.labelOrder = labelOrder;
        this.isEdgeComplete = isEdgeComplete;
        this.dataWF = dataWF;
        this.dataEquiv = dataEquiv;
        this.isDataComplete = isDataComplete;
    }

    @Override public java.util.Set<IResolutionPath<S, L, D>> resolve(S scope)
            throws ResolutionException, InterruptedException {
        return env(labelWF, Paths.empty(scope), Set.Immutable.of());
    }

    private Set<IResolutionPath<S, L, D>> env(LabelWF<L> re, IScopePath<S, L> path,
            Set.Immutable<IResolutionPath<S, L, D>> specifics) throws ResolutionException, InterruptedException {
        return env_L(labels, re, path, specifics);
    }

    // FIXME Use caching of single label environments to prevent recalculation in case of diamonds in
    // the graph
    private Set.Immutable<IResolutionPath<S, L, D>> env_L(Set.Immutable<L> L, LabelWF<L> re, IScopePath<S, L> path,
            Set.Immutable<IResolutionPath<S, L, D>> specifics) throws ResolutionException, InterruptedException {
        if(Thread.interrupted()) {
            throw new InterruptedException();
        }
        final Set.Transient<IResolutionPath<S, L, D>> env = Set.Transient.of();
        final Set<L> max_L = max(L);
        for(L l : max_L) {
            final Set.Immutable<IResolutionPath<S, L, D>> env1 = env_L(smaller(L, l), re, path, specifics);
            env.__insertAll(env1);
            if(env1.isEmpty() || !dataEquiv.alwaysTrue()) {
                final Set.Immutable<IResolutionPath<S, L, D>> env2 =
                        env_l(l, re, path, Set.Immutable.union(specifics, env1));
                env.__insertAll(env2);
            }
        }
        return env.freeze();
    }

    private Set.Immutable<IResolutionPath<S, L, D>> env_l(L l, LabelWF<L> re, IScopePath<S, L> path,
            Set.Immutable<IResolutionPath<S, L, D>> specifics) throws ResolutionException, InterruptedException {
        return l.equals(scopeGraph.getEndOfPath()) ? env_EOP(re, path, specifics) : env_nonEOP(l, re, path, specifics);
    }

    private Set.Immutable<IResolutionPath<S, L, D>> env_EOP(LabelWF<L> re, IScopePath<S, L> path,
            Set.Immutable<IResolutionPath<S, L, D>> specifics) throws ResolutionException, InterruptedException {
        if(!re.accepting()) {
            return Set.Immutable.of();
        }
        final S scope = path.getTarget();
        if(relation.map(r -> !isDataComplete.test(scope, r)).orElse(false)) {
            throw new IncompleteDataException(scope, relation.get());
        }
        final Set.Transient<IResolutionPath<S, L, D>> env = Set.Transient.of();
        if(relation.isPresent()) {
            final java.util.Set<List<D>> data = scopeGraph.getData().get(path.getTarget(), relation.get());
            for(List<D> datum : data) {
                if(dataWF.wf(datum) && notShadowed(datum, specifics)) {
                    env.__insert(Paths.resolve(path, relation, datum));
                }
            }
        } else {
            final List<D> datum = ImmutableList.of(scope);
            if(dataWF.wf(datum) && notShadowed(datum, specifics)) {
                env.__insert(Paths.resolve(path, relation, datum));
            }
        }
        return env.freeze();
    }

    private boolean notShadowed(List<D> datum, Set.Immutable<IResolutionPath<S, L, D>> specifics)
            throws ResolutionException, InterruptedException {
        for(IResolutionPath<S, L, D> p : specifics) {
            if(dataEquiv.leq(p.getDatum(), datum)) {
                return false;
            }
        }
        return true;
    }

    private Set.Immutable<IResolutionPath<S, L, D>> env_nonEOP(L l, LabelWF<L> re, IScopePath<S, L> path,
            Set.Immutable<IResolutionPath<S, L, D>> specifics) throws ResolutionException, InterruptedException {
        final Optional<LabelWF<L>> newRe = re.step(l);
        if(!newRe.isPresent()) {
            return Set.Immutable.of();
        }
        if(!isEdgeComplete.test(path.getTarget(), l)) {
            throw new IncompleteEdgeException(path.getTarget(), l);
        }
        final Set.Transient<IResolutionPath<S, L, D>> env = Set.Transient.of();
        for(S nextScope : scopeGraph.getEdges().get(path.getTarget(), l)) {
            final Optional<IScopePath<S, L>> p = Paths.append(path, Paths.edge(path.getTarget(), l, nextScope));
            if(p.isPresent()) {
                env.__insertAll(env(newRe.get(), p.get(), specifics));
            }
        }
        return env.freeze();
    }

    ///////////////////////////////////////////////////////////////////////////
    // max labels                                                            //
    ///////////////////////////////////////////////////////////////////////////

    private final Map<Set.Immutable<L>, Set.Immutable<L>> maxCache = Maps.newHashMap();

    private Set.Immutable<L> max(Set.Immutable<L> L) throws ResolutionException, InterruptedException {
        Set.Immutable<L> max;
        if((max = maxCache.get(L)) == null) {
            maxCache.put(L, (max = computeMax(L)));
        }
        return max;
    }

    private Set.Immutable<L> computeMax(Set.Immutable<L> L) throws ResolutionException, InterruptedException {
        final Set.Transient<L> max = Set.Transient.of();
        outer: for(L l1 : L) {
            for(L l2 : L) {
                if(labelOrder.lt(l1, l2)) {
                    continue outer;
                }
            }
            max.__insert(l1);
        }
        return max.freeze();
    }

    ///////////////////////////////////////////////////////////////////////////
    // smaller labels                                                        //
    ///////////////////////////////////////////////////////////////////////////

    private final Map<Tuple2<Set.Immutable<L>, L>, Set.Immutable<L>> smallerCache = Maps.newHashMap();

    private Set.Immutable<L> smaller(Set.Immutable<L> L, L l1) throws ResolutionException, InterruptedException {
        Tuple2<Set.Immutable<L>, L> key = ImmutableTuple2.of(L, l1);
        Set.Immutable<L> smaller;
        if((smaller = smallerCache.get(key)) == null) {
            smallerCache.put(key, (smaller = computeSmaller(L, l1)));
        }
        return smaller;
    }

    private Set.Immutable<L> computeSmaller(Set.Immutable<L> L, L l1) throws ResolutionException, InterruptedException {
        final Set.Transient<L> smaller = Set.Transient.of();
        for(L l2 : L) {
            if(labelOrder.lt(l2, l1)) {
                smaller.__insert(l2);
            }
        }
        return smaller.freeze();
    }

    ///////////////////////////////////////////////////////////////////////////
    // builder                                                               //
    ///////////////////////////////////////////////////////////////////////////

    public static <S extends D, L, D> Builder<S, L, D> builder() {
        return new Builder<>();
    }

    public static class Builder<S extends D, L, D> {

        private LabelWF<L> labelWF = LabelWF.ANY();
        private LabelOrder<L> labelOrder = LabelOrder.NONE();
        private Predicate2<S, L> isEdgeComplete = (s, l) -> true;

        private DataWF<D> dataWF = DataWF.ANY();
        private DataLeq<D> dataEquiv = DataLeq.NONE();
        private Predicate2<S, L> isDataComplete = (s, r) -> true;

        public Builder<S, L, D> withLabelWF(LabelWF<L> labelWF) {
            this.labelWF = labelWF;
            return this;
        }

        public Builder<S, L, D> withLabelOrder(LabelOrder<L> labelOrder) {
            this.labelOrder = labelOrder;
            return this;
        }

        public Builder<S, L, D> withEdgeComplete(Predicate2<S, L> isEdgeComplete) {
            this.isEdgeComplete = isEdgeComplete;
            return this;
        }

        public Builder<S, L, D> withDataWF(DataWF<D> dataWF) {
            this.dataWF = dataWF;
            return this;
        }

        public Builder<S, L, D> withDataEquiv(DataLeq<D> dataEquiv) {
            this.dataEquiv = dataEquiv;
            return this;
        }

        public Builder<S, L, D> withDataComplete(Predicate2<S, L> isDataComplete) {
            this.isDataComplete = isDataComplete;
            return this;
        }

        public FastNameResolution<S, L, D> build(IScopeGraph<S, L, D> scopeGraph, Optional<L> relation) {
            return new FastNameResolution<>(scopeGraph, relation, labelWF, labelOrder, isEdgeComplete, dataWF,
                    dataEquiv, isDataComplete);
        }

    }

}