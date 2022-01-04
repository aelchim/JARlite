import java.util.Set;
import jdk.internal.org.objectweb.asm.commons.Remapper;

class Collector extends Remapper {

    private final Set<Class<?>> classNames;
    private final String prefix;

    public Collector(final Set<Class<?>> classNames, final String prefix) {
        this.classNames = classNames;
        this.prefix = prefix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String mapDesc(final String desc) {
        if (desc.startsWith("L")) {
            this.addType(desc.substring(1, desc.length() - 1));
        }
        return super.mapDesc(desc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String[] mapTypes(final String[] types) {
        for (final String type : types) {
            this.addType(type);
        }
        return super.mapTypes(types);
    }

    private void addType(final String type) {
        final String className = type.replace('/', '.');
        if (className.startsWith(this.prefix)) {
            try {
                this.classNames.add(Class.forName(className));
            } catch (final ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    String mapType(final String type) {
        this.addType(type);
        return type;
    }
}