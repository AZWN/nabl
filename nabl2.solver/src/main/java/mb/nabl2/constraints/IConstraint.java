package mb.nabl2.constraints;

import java.util.function.Function;

import org.metaborg.util.functions.CheckedFunction1;

import mb.nabl2.constraints.ast.IAstConstraint;
import mb.nabl2.constraints.base.IBaseConstraint;
import mb.nabl2.constraints.equality.IEqualityConstraint;
import mb.nabl2.constraints.messages.IMessageContent;
import mb.nabl2.constraints.messages.IMessageInfo;
import mb.nabl2.constraints.nameresolution.INameResolutionConstraint;
import mb.nabl2.constraints.relations.IRelationConstraint;
import mb.nabl2.constraints.scopegraph.IScopeGraphConstraint;
import mb.nabl2.constraints.sets.ISetConstraint;
import mb.nabl2.constraints.sym.ISymbolicConstraint;

public interface IConstraint {

    IMessageInfo getMessageInfo();

    IMessageContent pp();

    <T> T match(Cases<T> function);

    interface Cases<T> {

        T caseAst(IAstConstraint constraint);

        T caseBase(IBaseConstraint constraint);

        T caseEquality(IEqualityConstraint constraint);

        T caseScopeGraph(IScopeGraphConstraint constraint);

        T caseNameResolution(INameResolutionConstraint constraint);

        T caseRelation(IRelationConstraint constraint);

        T caseSet(ISetConstraint constraint);

        T caseSym(ISymbolicConstraint constraint);

        // @formatter:off
        static <T> Cases<T> of(
            Function<IAstConstraint,T> onAst,
            Function<IBaseConstraint,T> onBase,
            Function<IEqualityConstraint,T> onEquality,
            Function<IScopeGraphConstraint,T> onScopeGraph,
            Function<INameResolutionConstraint,T> onNameResolution,
            Function<IRelationConstraint,T> onRelation,
            Function<ISetConstraint,T> onSet,
            Function<ISymbolicConstraint,T> onSym
        ) /* @formatter:on */ {
            return new Cases<T>() {

                @Override public T caseAst(IAstConstraint constraint) {
                    return onAst.apply(constraint);
                }

                @Override public T caseBase(IBaseConstraint constraint) {
                    return onBase.apply(constraint);
                }

                @Override public T caseEquality(IEqualityConstraint constraint) {
                    return onEquality.apply(constraint);
                }

                @Override public T caseScopeGraph(IScopeGraphConstraint constraint) {
                    return onScopeGraph.apply(constraint);
                }

                @Override public T caseNameResolution(INameResolutionConstraint constraint) {
                    return onNameResolution.apply(constraint);
                }

                @Override public T caseRelation(IRelationConstraint constraint) {
                    return onRelation.apply(constraint);
                }

                @Override public T caseSet(ISetConstraint constraint) {
                    return onSet.apply(constraint);
                }

                @Override public T caseSym(ISymbolicConstraint constraint) {
                    return onSym.apply(constraint);
                }

            };
        }

        static <T> Builder<T> builder() {
            return new Builder<>();
        }

        static class Builder<T> {

            private Function<? super IAstConstraint, T> onAst = null;
            private Function<? super IBaseConstraint, T> onBase = null;
            private Function<? super IEqualityConstraint, T> onEquality = null;
            private Function<? super IScopeGraphConstraint, T> onScopeGraph = null;
            private Function<? super INameResolutionConstraint, T> onNameResolution = null;
            private Function<? super IRelationConstraint, T> onRelation = null;
            private Function<? super ISetConstraint, T> onSet = null;
            private Function<? super ISymbolicConstraint, T> onSym = null;

            public Builder<T> onAst(Function<? super IAstConstraint, T> onAst) {
                this.onAst = onAst;
                return this;
            }

            public Builder<T> onBase(Function<? super IBaseConstraint, T> onBase) {
                this.onBase = onBase;
                return this;
            }

            public Builder<T> onEquality(Function<? super IEqualityConstraint, T> onEquality) {
                this.onEquality = onEquality;
                return this;
            }

            public Builder<T> onScopeGraph(Function<? super IScopeGraphConstraint, T> onScopeGraph) {
                this.onScopeGraph = onScopeGraph;
                return this;
            }

            public Builder<T> onNameResolution(Function<? super INameResolutionConstraint, T> onNameResolution) {
                this.onNameResolution = onNameResolution;
                return this;
            }

            public Builder<T> onRelation(Function<? super IRelationConstraint, T> onRelation) {
                this.onRelation = onRelation;
                return this;
            }

            public Builder<T> onSet(Function<? super ISetConstraint, T> onSet) {
                this.onSet = onSet;
                return this;
            }

            public Builder<T> onSym(Function<? super ISymbolicConstraint, T> onSym) {
                this.onSym = onSym;
                return this;
            }

            public Cases<T> otherwise(Function<? super IConstraint, T> otherwise) {
                return new Cases<T>() {

                    @Override public T caseAst(IAstConstraint constraint) {
                        return(onAst != null ? onAst.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseBase(IBaseConstraint constraint) {
                        return(onBase != null ? onBase.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseEquality(IEqualityConstraint constraint) {
                        return(onEquality != null ? onEquality.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseScopeGraph(IScopeGraphConstraint constraint) {
                        return(onScopeGraph != null ? onScopeGraph.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseNameResolution(INameResolutionConstraint constraint) {
                        return(onNameResolution != null ? onNameResolution.apply(constraint)
                                : otherwise.apply(constraint));
                    }

                    @Override public T caseRelation(IRelationConstraint constraint) {
                        return(onRelation != null ? onRelation.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseSet(ISetConstraint constraint) {
                        return(onSet != null ? onSet.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseSym(ISymbolicConstraint constraint) {
                        return(onSym != null ? onSym.apply(constraint) : otherwise.apply(constraint));
                    }

                };
            }

        }

    }

    <T, E extends Throwable> T matchOrThrow(CheckedCases<T, E> function) throws E;

    interface CheckedCases<T, E extends Throwable> {

        T caseAst(IAstConstraint constraint) throws E;

        T caseBase(IBaseConstraint constraint) throws E;

        T caseEquality(IEqualityConstraint constraint) throws E;

        T caseScopeGraph(IScopeGraphConstraint constraint) throws E;

        T caseNameResolution(INameResolutionConstraint constraint) throws E;

        T caseRelation(IRelationConstraint constraint) throws E;

        T caseSet(ISetConstraint constraint) throws E;

        T caseSym(ISymbolicConstraint cFact) throws E;

        // @formatter:off
        static <T, E extends Throwable> CheckedCases<T, E> of(
            CheckedFunction1<IAstConstraint,T,E> onAst,
            CheckedFunction1<IBaseConstraint,T,E> onBase,
            CheckedFunction1<IEqualityConstraint,T,E> onEquality,
            CheckedFunction1<IScopeGraphConstraint,T,E> onScopeGraph,
            CheckedFunction1<INameResolutionConstraint,T,E> onNameResolution,
            CheckedFunction1<IRelationConstraint,T,E> onRelation,
            CheckedFunction1<ISetConstraint,T,E> onSet,
            CheckedFunction1<ISymbolicConstraint,T,E> onSym
        ) /* @formatter:on */ {
            return new CheckedCases<T, E>() {

                @Override public T caseAst(IAstConstraint constraint) throws E {
                    return onAst.apply(constraint);
                }

                @Override public T caseBase(IBaseConstraint constraint) throws E {
                    return onBase.apply(constraint);
                }

                @Override public T caseEquality(IEqualityConstraint constraint) throws E {
                    return onEquality.apply(constraint);
                }

                @Override public T caseScopeGraph(IScopeGraphConstraint constraint) throws E {
                    return onScopeGraph.apply(constraint);
                }

                @Override public T caseNameResolution(INameResolutionConstraint constraint) throws E {
                    return onNameResolution.apply(constraint);
                }

                @Override public T caseRelation(IRelationConstraint constraint) throws E {
                    return onRelation.apply(constraint);
                }

                @Override public T caseSet(ISetConstraint constraint) throws E {
                    return onSet.apply(constraint);
                }

                @Override public T caseSym(ISymbolicConstraint constraint) throws E {
                    return onSym.apply(constraint);
                }

            };
        }


        static <T, E extends Throwable> Builder<T, E> builder() {
            return new Builder<>();
        }

        static class Builder<T, E extends Throwable> {

            private CheckedFunction1<? super IAstConstraint, T, E> onAst = null;
            private CheckedFunction1<? super IBaseConstraint, T, E> onBase = null;
            private CheckedFunction1<? super IEqualityConstraint, T, E> onEquality = null;
            private CheckedFunction1<? super IScopeGraphConstraint, T, E> onScopeGraph = null;
            private CheckedFunction1<? super INameResolutionConstraint, T, E> onNameResolution = null;
            private CheckedFunction1<? super IRelationConstraint, T, E> onRelation = null;
            private CheckedFunction1<? super ISetConstraint, T, E> onSet = null;
            private CheckedFunction1<? super ISymbolicConstraint, T, E> onSym = null;

            public Builder<T, E> onAst(CheckedFunction1<? super IAstConstraint, T, E> onAst) {
                this.onAst = onAst;
                return this;
            }

            public Builder<T, E> onBase(CheckedFunction1<? super IBaseConstraint, T, E> onBase) {
                this.onBase = onBase;
                return this;
            }

            public Builder<T, E> onEquality(CheckedFunction1<? super IEqualityConstraint, T, E> onEquality) {
                this.onEquality = onEquality;
                return this;
            }

            public Builder<T, E> onScopeGraph(CheckedFunction1<? super IScopeGraphConstraint, T, E> onScopeGraph) {
                this.onScopeGraph = onScopeGraph;
                return this;
            }

            public Builder<T, E>
                    onNameResolution(CheckedFunction1<? super INameResolutionConstraint, T, E> onNameResolution) {
                this.onNameResolution = onNameResolution;
                return this;
            }

            public Builder<T, E> onRelation(CheckedFunction1<? super IRelationConstraint, T, E> onRelation) {
                this.onRelation = onRelation;
                return this;
            }

            public Builder<T, E> onSet(CheckedFunction1<? super ISetConstraint, T, E> onSet) {
                this.onSet = onSet;
                return this;
            }

            public Builder<T, E> onSym(CheckedFunction1<? super ISymbolicConstraint, T, E> onSym) {
                this.onSym = onSym;
                return this;
            }

            public CheckedCases<T, E> otherwise(CheckedFunction1<? super IConstraint, T, E> otherwise) {
                return new CheckedCases<T, E>() {

                    @Override public T caseAst(IAstConstraint constraint) throws E {
                        return(onAst != null ? onAst.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseBase(IBaseConstraint constraint) throws E {
                        return(onBase != null ? onBase.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseEquality(IEqualityConstraint constraint) throws E {
                        return(onEquality != null ? onEquality.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseScopeGraph(IScopeGraphConstraint constraint) throws E {
                        return(onScopeGraph != null ? onScopeGraph.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseNameResolution(INameResolutionConstraint constraint) throws E {
                        return(onNameResolution != null ? onNameResolution.apply(constraint)
                                : otherwise.apply(constraint));
                    }

                    @Override public T caseRelation(IRelationConstraint constraint) throws E {
                        return(onRelation != null ? onRelation.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseSet(ISetConstraint constraint) throws E {
                        return(onSet != null ? onSet.apply(constraint) : otherwise.apply(constraint));
                    }

                    @Override public T caseSym(ISymbolicConstraint constraint) throws E {
                        return(onSym != null ? onSym.apply(constraint) : otherwise.apply(constraint));
                    }

                };
            }

        }

    }

}