package org.oryxel.viabedrockutility.attachable;

import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.Value;

import java.util.Optional;

final class AttachableQueryBinding extends MutableObjectBinding {
    private final AttachableQueryContext context;
    private final String prefix;

    AttachableQueryBinding(AttachableQueryContext context) {
        this(context, "");
    }

    private AttachableQueryBinding(AttachableQueryContext context, String prefix) {
        this.context = context;
        this.prefix = prefix;
    }

    @Override
    public ObjectProperty getProperty(String name) {
        final ObjectProperty property = super.getProperty(name);
        if (property != null) {
            return property;
        }

        final String fullName = prefix.isEmpty() ? name : prefix + "." + name;
        final Optional<AttachableQueryValue> provided = AttachableQueryProviders.resolveIfHandled(context, fullName);
        if (provided.isPresent()) {
            return ObjectProperty.property(toMocha(provided.get()), false);
        }
        if (AttachableQueryProviders.isNamespace(fullName)) {
            return ObjectProperty.property(new AttachableQueryBinding(context, fullName), false);
        }

        final Optional<AttachableQueryValue> fallback = AttachableQueryProviders.resolve(
                context, fullName, "query." + fullName);
        return fallback.<ObjectProperty>map(value -> ObjectProperty.property(toMocha(value), false)).orElse(null);
    }

    private static Value toMocha(AttachableQueryValue value) {
        return switch (value.kind()) {
            case NUMBER -> Value.of(value.number());
            case BOOLEAN -> Value.of(value.booleanValue() ? 1.0D : 0.0D);
            case STRING -> Value.of(value.stringValue());
        };
    }
}
