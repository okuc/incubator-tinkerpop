/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.filter;

import org.apache.tinkerpop.gremlin.process.computer.MemoryComputeKey;
import org.apache.tinkerpop.gremlin.process.traversal.Operator;
import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.Barrier;
import org.apache.tinkerpop.gremlin.process.traversal.step.ByModulating;
import org.apache.tinkerpop.gremlin.process.traversal.step.Bypassing;
import org.apache.tinkerpop.gremlin.process.traversal.step.Scoping;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.util.TraverserSet;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalUtil;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedFactory;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BinaryOperator;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class DedupGlobalStep<S> extends FilterStep<S> implements TraversalParent, Scoping, Bypassing, Barrier<TraverserSet<S>>, ByModulating {

    private Traversal.Admin<S, Object> dedupTraversal = null;
    private Set<Object> duplicateSet = new HashSet<>();
    private boolean bypass = false;
    private final Set<String> dedupLabels;

    public DedupGlobalStep(final Traversal.Admin traversal, final String... dedupLabels) {
        super(traversal);
        this.dedupLabels = dedupLabels.length == 0 ? null : Collections.unmodifiableSet(new HashSet<>(Arrays.asList(dedupLabels)));
    }

    @Override
    protected boolean filter(final Traverser.Admin<S> traverser) {
        if (this.bypass) return true;
        traverser.setBulk(1);
        if (null == this.dedupLabels) {
            return this.duplicateSet.add(TraversalUtil.applyNullable(traverser, this.dedupTraversal));
        } else {
            final List<Object> objects = new ArrayList<>(this.dedupLabels.size());
            this.dedupLabels.forEach(label -> objects.add(TraversalUtil.applyNullable((S) this.getScopeValue(Pop.last, label, traverser), this.dedupTraversal)));
            return this.duplicateSet.add(objects);
        }
    }

    @Override
    public List<Traversal<S, Object>> getLocalChildren() {
        return null == this.dedupTraversal ? Collections.emptyList() : Collections.singletonList(this.dedupTraversal);
    }

    @Override
    public void modulateBy(final Traversal.Admin<?, ?> dedupTraversal) {
        this.dedupTraversal = this.integrateChild(dedupTraversal);
    }

    @Override
    public DedupGlobalStep<S> clone() {
        final DedupGlobalStep<S> clone = (DedupGlobalStep<S>) super.clone();
        clone.duplicateSet = new HashSet<>();
        if (null != this.dedupTraversal)
            clone.dedupTraversal = this.dedupTraversal.clone();
        return clone;
    }

    @Override
    public void setTraversal(final Traversal.Admin<?, ?> parentTraversal) {
        super.setTraversal(parentTraversal);
        integrateChild(this.dedupTraversal);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        if (this.dedupTraversal != null)
            result ^= this.dedupTraversal.hashCode();
        if (this.dedupLabels != null)
            result ^= this.dedupLabels.hashCode();
        return result;
    }

    @Override
    public void reset() {
        super.reset();
        this.duplicateSet.clear();
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, this.dedupLabels, this.dedupTraversal);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.dedupLabels == null ?
                this.getSelfAndChildRequirements(TraverserRequirement.BULK) :
                this.getSelfAndChildRequirements(TraverserRequirement.LABELED_PATH, TraverserRequirement.BULK);
    }

    @Override
    public void setBypass(final boolean bypass) {
        this.bypass = bypass;
    }

    @Override
    public Set<String> getScopeKeys() {
        return null == this.dedupLabels ? Collections.emptySet() : this.dedupLabels;
    }

    @Override
    public void processAllStarts() {

    }

    @Override
    public boolean hasNextBarrier() {
        return this.starts.hasNext();
    }

    @Override
    public TraverserSet<S> nextBarrier() throws NoSuchElementException {
        final TraverserSet<S> traverserSet = new TraverserSet<>();
        while (this.starts.hasNext()) {
            final Traverser.Admin<S> traverser = this.starts.next();
            traverser.set(DetachedFactory.detach(traverser.get(), true));
            traverserSet.add(traverser);
        }
        if (traverserSet.isEmpty())
            throw FastNoSuchElementException.instance();
        else
            return traverserSet;
    }

    @Override
    public void addBarrier(final TraverserSet<S> barrier) {
        IteratorUtils.removeOnNext(barrier.iterator()).forEachRemaining(traverser -> {
            traverser.setSideEffects(this.getTraversal().getSideEffects());
            this.addStart(traverser);
        });
    }

    @Override
    public MemoryComputeKey<TraverserSet<S>> getMemoryComputeKey() {
        return MemoryComputeKey.of(this.getId(), (BinaryOperator) Operator.addAll, false, true);
    }

}
