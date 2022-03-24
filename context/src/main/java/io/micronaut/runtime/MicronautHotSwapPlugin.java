/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.runtime;

import com.oracle.truffle.espresso.hotswap.EspressoHotSwap;
import com.oracle.truffle.espresso.hotswap.HotSwapPlugin;
import io.micronaut.context.AbstractExecutableMethodsDefinition;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.inject.BeanDefinitionReference;

import java.io.IOException;

final class MicronautHotSwapPlugin implements HotSwapPlugin {

    private final ApplicationContext context;
    private boolean needsBeenRefresh = false;

    MicronautHotSwapPlugin(ApplicationContext context) {
        this.context = context;

        // register class re-init for classes that provide annotation metadata
        EspressoHotSwap.registerClassInitHotSwap(AnnotationMetadataProvider.class, true, () -> {
            needsBeenRefresh = true;
        });
        // register class re-init for classes that are executable methods
        EspressoHotSwap.registerClassInitHotSwap(AbstractExecutableMethodsDefinition.class, true, () -> {
            needsBeenRefresh = true;
        });
        // register ServiceLoader listener for declared bean definitions
        try {
            EspressoHotSwap.registerMetaInfServicesListener(BeanDefinitionReference.class, context.getClassLoader(), () -> reloadContext());
            EspressoHotSwap.registerMetaInfServicesListener(BeanIntrospectionReference.class, context.getClassLoader(), () -> reloadContext());
        } catch (IOException e) {
            if (Micronaut.LOG.isInfoEnabled()) {
                Micronaut.LOG.info("Failed to setup META-INF/services reloading");
            }
        }
    }

    @Override
    public String getName() {
        return "Micronaut HotSwap Plugin";
    }

    @Override
    public void postHotSwap(Class<?>[] changedClasses) {
        if (needsBeenRefresh) {
            reloadContext();
        }
        needsBeenRefresh = false;
    }

    private void reloadContext() {
        if (Micronaut.LOG.isInfoEnabled()) {
            Micronaut.LOG.info("Reloading app context");
        }
        long start = System.currentTimeMillis();
        // full stop/restart of context
        context.stop();
        context.flushBeanCaches();
        context.start();

        // fetch new embedded application bean which will re-wire beans
        context.findBean(EmbeddedApplication.class).ifPresent(ApplicationContextLifeCycle::start);

        if (Micronaut.LOG.isInfoEnabled()) {
            Micronaut.LOG.info("Done reloading app context in " + (System.currentTimeMillis() - start) + " ms.");
        }
    }
}
