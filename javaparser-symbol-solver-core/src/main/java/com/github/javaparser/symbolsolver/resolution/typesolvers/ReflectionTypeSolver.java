/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.javaparser.symbolsolver.resolution.typesolvers;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionFactory;

import java.util.Optional;

/**
 * Uses reflection to resolve types.
 * Classes on the classpath used to run your application will be found.
 * No source code is available for the resolved types.
 *
 * @author Federico Tomassetti
 */
public class ReflectionTypeSolver implements TypeSolver {

    private TypeSolver parent;

    private final boolean jreOnly;

    /**
     * @param jreOnly if true, will only resolve types from the java or javax packages.
     * This is an easy way to say "I need a JRE to solve classes, and the one that is currently running is fine."
     * If false, will resolve any kind of type.
     */
    public ReflectionTypeSolver(boolean jreOnly) {
        this.jreOnly = jreOnly;
    }

    /**
     * Resolves classes from the JRE that is currently running.
     * (It calls the other constructor with "true".)
     */
    public ReflectionTypeSolver() {
        this(true);
    }

    @Override
    public TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(TypeSolver parent) {
        this.parent = parent;
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        if (!jreOnly || (name.startsWith("java.") || name.startsWith("javax."))) {
            try {
                ClassLoader classLoader = ReflectionTypeSolver.class.getClassLoader();

                // Some implementations could return null when the class was loaded through the bootstrap classloader
                // see https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html#getClassLoader--
                if (classLoader == null) {
                    throw new RuntimeException("The ReflectionTypeSolver has been probably loaded through the bootstrap class loader. This usage is not supported by the JavaSymbolSolver");
                }

                Class<?> clazz = classLoader.loadClass(name);
                return SymbolReference.solved(ReflectionFactory.typeDeclarationFor(clazz, getRoot()));
            } catch (NoClassDefFoundError e) {
                // We can safely ignore this one because it is triggered when there are package names which are almost the
                // same as class name, with the exclusion of the case.
                // For example:
                // java.lang.NoClassDefFoundError: com/github/javaparser/printer/ConcreteSyntaxModel
                // (wrong name: com/github/javaparser/printer/concretesyntaxmodel)
                // note that this exception seems to be thrown only on certain platform (mac yes, linux no)
                return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
            } catch (ClassNotFoundException e) {
                // it could be an inner class
                int lastDot = name.lastIndexOf('.');
                if (lastDot == -1) {
                    return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
                } else {
                    String parentName = name.substring(0, lastDot);
                    String childName = name.substring(lastDot + 1);
                    SymbolReference<ResolvedReferenceTypeDeclaration> parent = tryToSolveType(parentName);
                    if (parent.isSolved()) {
                        Optional<ResolvedReferenceTypeDeclaration> innerClass = parent.getCorrespondingDeclaration().internalTypes()
                                .stream().filter(it -> it.getName().equals(childName)).findFirst();
                        if (innerClass.isPresent()) {
                            return SymbolReference.solved(innerClass.get());
                        } else {
                            return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
                        }
                    } else {
                        return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
                    }
                }
            }
        } else {
            return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
        }
    }

}
