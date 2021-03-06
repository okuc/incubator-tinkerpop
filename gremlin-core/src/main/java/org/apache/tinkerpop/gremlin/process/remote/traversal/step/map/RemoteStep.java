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
package org.apache.tinkerpop.gremlin.process.remote.traversal.step.map;

import org.apache.tinkerpop.gremlin.process.remote.RemoteConnection;
import org.apache.tinkerpop.gremlin.process.remote.RemoteConnectionException;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.AbstractStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.PureTraversal;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Sends a {@link Traversal} to a {@link RemoteConnection} and iterates back the results.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class RemoteStep<S,E> extends AbstractStep<S, E> {
    private transient RemoteConnection remoteConnection;
    private PureTraversal pureTraversal;
    private Iterator<Traverser> currentIterator;

    public RemoteStep(final Traversal.Admin<S,E> traversal, final Traversal traversalToRemote,
                      final RemoteConnection remoteConnection) {
        super(traversal);
        this.remoteConnection = remoteConnection;
        pureTraversal = new PureTraversal<>(traversalToRemote.asAdmin());
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, pureTraversal.get());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Traverser.Admin<E> processNextStart() throws NoSuchElementException {

        if (null == currentIterator) {
            try {
                final Traversal.Admin temp = pureTraversal.getPure();
                temp.setStrategies(this.getTraversal().getStrategies().clone());
                currentIterator = remoteConnection.submit(temp);
            } catch (RemoteConnectionException sce) {
                throw new IllegalStateException(sce);
            } catch (IllegalStateException ex) {

            }
        }

        return this.currentIterator.next().asAdmin();
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.pureTraversal.hashCode();
    }
}
