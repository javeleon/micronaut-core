package io.micronaut.runtime;

import com.oracle.truffle.espresso.hotswap.EspressoHotSwap;
import com.oracle.truffle.espresso.hotswap.HotSwapPlugin;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.Optional;

final class MicronautHotSwapPlugin implements HotSwapPlugin {

    private final ApplicationContext context;
    private boolean needsBeenRefresh = false;

    MicronautHotSwapPlugin(ApplicationContext context) {
        this.context = context;
        // register class re-init for classes that provide annotation metadata
        EspressoHotSwap.registerClassInitHotSwap(AnnotationMetadataProvider.class, true, () -> needsBeenRefresh = true);
        // register ServiceLoader listener for declared bean definitions
        EspressoHotSwap.registerMetaInfServicesListener(BeanDefinitionReference.class, context.getClassLoader(), () -> reloadContext());
        EspressoHotSwap.registerMetaInfServicesListener(BeanIntrospectionReference.class, context.getClassLoader(), () -> reloadContext());
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
        context.stop();
        context.flushBeanCaches();
        context.start();

        // fetch new embedded application bean which will re-wire beans
        Optional<EmbeddedApplication> bean = context.findBean(EmbeddedApplication.class);
        // now restart the embedded app/server
        bean.ifPresent(ApplicationContextLifeCycle::start);
    }
}
