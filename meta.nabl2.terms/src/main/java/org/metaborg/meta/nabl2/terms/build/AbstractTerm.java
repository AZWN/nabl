package org.metaborg.meta.nabl2.terms.build;

import org.immutables.value.Value;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.Terms;

import com.google.common.collect.ImmutableClassToInstanceMap;

public abstract class AbstractTerm implements ITerm {

    @Value.Auxiliary @Value.Default public ImmutableClassToInstanceMap<Object> getAttachments() {
        return Terms.NO_ATTACHMENTS;
    }

}