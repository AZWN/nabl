package mb.statix.taico.scopegraph;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import mb.nabl2.terms.ITerm;
import mb.nabl2.util.collections.IRelation3;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.taico.module.IModule;

public interface IMInternalScopeGraph<S, L, D> extends IMExternalScopeGraph<S, L, D> {
    /**
     * @param sourceScope
     *      the source scope
     * @param label
     *      the label
     * @param targetScope
     *      the target scope
     * 
     * @return
     *      true if this edge was added, false if it already existed
     */
    boolean addEdge(S sourceScope, L label, S targetScope);
    
    /**
     * @param scope
     *      the scope
     * @param relation
     *      the relation
     * @param datum
     *      the datum
     * 
     * @return
     *      true if this scope graph changed as a result of this call, false otherwise
     */
    boolean addDatum(S scope, L relation, D datum);
    
    
    /**
     * Creates a new scope in this scope graph.
     * 
     * @param base
     *      the base string to use for the scope name
     * 
     * @return
     *      the newly created scope
     * 
     * @deprecated
     *      Use {@link #createScopeWithIdentity(String)} instead.
     */
    @Deprecated
    S createScope(String base);
    
    /**
     * Creates a new scope in this scope graph, with the given identity.
     * 
     * @param identity
     *      the scope name (identity) this contains the creation path
     * 
     * @return
     *      the newly created scope
     */
    S createScopeWithIdentity(String identity);
    
    /**
     * Gets the collection of edges from the given scope with the given label, that are
     * either edges owned by the current scope graph, or owned by any of its children.
     * 
     * @param scope
     *      the scope to start from
     * @param label
     *      the label for the edges
     * 
     * @return
     *      a set with all the edges
     */
    default Set<S> getTransitiveEdges(S scope, L label) {
        Set<S> edges = new HashSet<>();
        getTransitiveEdges(scope, label, edges);
        return edges;
    }
    
    /**
     * Gets the collection of data from the given scope with the given label, that are
     * either data owned by the current scope graph, or owned by any of its children.
     * 
     * @param scope
     *      the scope to start from
     * @param label
     *      the label for the data
     * 
     * @return
     *      a set with all the data
     */
    default Set<D> getTransitiveData(S scope, L label) {
        Set<D> data = new HashSet<>();
        getTransitiveData(scope, label, data);
        return data;
    }
    
    /**
     * Gets the collection of edges from the given scope with the given label, that are
     * either edges owned by the current scope graph, or owned by any of its children.
     * 
     * @param scope
     *      the scope to start from
     * @param label
     *      the label for the edges
     * @param edges
     *      the collection to add the edges to
     * 
     * @return
     *      a set with all the edges
     */
    void getTransitiveEdges(S scope, L label, Collection<S> edges);
    
    /**
     * Gets the collection of data from the given scope with the given label, that are
     * either data owned by the current scope graph, or owned by any of its children.
     * 
     * @param scope
     *      the scope to start from
     * @param label
     *      the label for the data
     * @param data
     *      the collection to add the data to
     * 
     * @return
     *      a set with all the data
     */
    void getTransitiveData(S scope, L label, Collection<D> data);
    
    //Scope graph tree
    
    /**
     * Creates a child scope graph from this scope graph. The returned scope graph must be added
     * with the addChild method.
     * 
     * @param module
     *      the module that will own the child graph
     * @param canExtend
     *      the scopes that this child can extend, in the order they are encountered
     * @return
     *      the new scope graph
     */
    IMInternalScopeGraph<S, L, D> createChild(IModule module, List<S> canExtend);
    
    /**
     * Adds the given module as a child to this scope graph.
     * 
     * @param child
     *      the child module
     * 
     * @return
     *      the child scope graph
     */
    IMInternalScopeGraph<S, L, D> addChild(IModule child);
    
    /**
     * @return
     *      an iterable of all the children of this scope graph
     */
    Iterable<? extends IMInternalScopeGraph<S, L, D>> getChildren();
    
    /**
     * Removes the given child module.
     * 
     * @param child
     *      the child module to remove
     * 
     * @return
     *      true if this scope graph changed as a result of this call, false otherwise
     */
    boolean removeChild(IModule child);
    
    /**
     * Removes all children of this module (transitively).
     */
    void purgeChildren();
    
    //Getters for the internal data structures of the scope graph
    
    /**
     * @return
     *      a relation of all the edges directly owned by this module
     */
    IRelation3<S, L, S> getOwnEdges();
    
    /**
     * @return
     *      a relation of all the data edges directly owned by this module
     */
    IRelation3<S, L, D> getOwnData();
    
    /**
     * @return
     *      a set of all the scopes owned by this module directly
     */
    Set<S> getScopes();
    
    /**
     * @return
     *      the set of scopes that can be extended by this scope graph
     */
    io.usethesource.capsule.Set.Immutable<? extends S> getExtensibleScopes();
    
    /**
     * @return
     *      the list of parent scopes, in the order we received them
     */
    List<? extends S> getParentScopes();
    
    /**
     * @return
     *      an external view on this scope graph
     */
    IMExternalScopeGraph<S, L, D> externalGraph();
    
    /**
     * @param clearScopes
     *      if true, scopes of the original are cleared in the delegate
     * 
     * @return
     *      a delegating scope graph
     */
    IMInternalScopeGraph<S, L, D> delegatingGraph(boolean clearScopes);
    
    /**
     * Substitutes old parent scopes (extensible scopes) with the given new scopes.
     * 
     * @param newScopes
     *      the new scopes
     * 
     * @throws IllegalArgumentException
     *      If the number of new scopes is not the same as the number of old scopes.
     */
    void substitute(List<? extends S> newScopes);
    
    /**
     * @return
     *      the write lock for this scope graph (not for children)
     */
    Lock getWriteLock();

    
    /**
     * @param module
     *      the new owner
     * 
     * @return
     *      the copy
     */
    IMInternalScopeGraph<Scope, ITerm, ITerm> copy(IModule module);
}
