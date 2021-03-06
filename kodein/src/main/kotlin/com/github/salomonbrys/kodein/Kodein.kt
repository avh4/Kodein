package com.github.salomonbrys.kodein

import java.lang.reflect.Type

/**
 * KOtlin DEpendency INjection.
 *
 * To construct a Kodein instance, simply use it's block constructor and define your bindings in it :
 *
 * ```kotlin
 * val kodein = Kodein {
 *     bind<Type1>() with factory { arg: Arg -> ** provide a Type1 function arg ** }
 *     bind<Type2>() with provide { ** provide a Type1 ** }
 * }
 * ```
 *
 * See the file scopes.kt for other scopes.
 */
class Kodein internal constructor(val _container: Container) {

    data class Bind(
            val type: Type,
            val tag: Any?
    ) {
        override fun toString() = "bind<${type.dispName}>(${ if (tag != null) "\"$tag\"" else "" })"
    }

    data class Key(
            val bind: Bind,
            val argType: Type
    ) {
        override fun toString() = buildString {
            if (bind.tag != null) append("\"${bind.tag}\": ")
            if (argType != Unit.javaClass) append("(${argType.dispName})") else append("()")
            append("-> ${bind.type.dispName}")
        }
    }

    /**
     * A module is used the same way as in the Kodein constructor :
     *
     * ```kotlin
     * val module = Kodein.Module {
     *     bind<Type2>() with provide { ** provide a Type1 ** }
     * }
     * ```
     */
    class Module(val init: Builder.() -> Unit)

    /**
     * Allows for the DSL inside the block argument of the constructor of `Kodein` and `Kodein.Module`
     */
    class Builder(init: Builder.() -> Unit) {

        internal val _builder = Container.Builder()

        init { this.init() }

        inner class TypeBinder<T : Any>(private val bind: Bind) {
            infix fun <R : T, A> with(factory: Factory<A, R>) = _builder.bind(Key(bind, factory.argType), factory)
        }

        inner class ConstantBinder(private val tag: Any) {
            infix fun with(value: Any) = _builder.bind(Key(Bind(value.javaClass, tag), Unit.javaClass), instance(value))
        }

        inline fun <reified T : Any> bind(tag: Any? = null): TypeBinder<T> = TypeBinder(Bind(typeToken<T>(), tag))

        fun constant(tag: Any): ConstantBinder = ConstantBinder(tag)

        fun import(module: Module) = module.init.invoke(this)

        fun extend(kodein: Kodein) = _builder.extend(kodein._container)
    }

    constructor(init: Kodein.Builder.() -> Unit) : this(Container(Builder(init)._builder))

    /**
     * This is for debug. It allows to print all binded keys.
     */
    val registeredBindings: Map<Kodein.Bind, String> get() = _container.registeredBindings

    val bindingsDescription: String get() = _container.bindingsDescription

    /**
     * Exception thrown when there is a dependency loop.
     */
    class DependencyLoopException internal constructor(message: String) : RuntimeException(message)

    /**
     * Exception thrown when asked for a dependency that cannot be found
     */
    class NotFoundException(message: String) : RuntimeException(message)

    /**
     * Gets a factory for the given argument type, return type and tag.
     */
    inline fun <reified A, reified T : Any> factory(tag: Any? = null): ((A) -> T) = _container.nonNullFactory<A, T>(Kodein.Key(Kodein.Bind(typeToken<T>(), tag), typeToken<A>()))

    /**
     * Gets a factory for the given argument type, return type and tag, or null if non is found.
     */
    inline fun <reified A, reified T : Any> factoryOrNull(tag: Any? = null): ((A) -> T)? = _container.factoryOrNull<A, T>(Kodein.Key(Kodein.Bind(typeToken<T>(), tag), typeToken<A>()))

    /**
     * Gets a provider for the given type and tag.
     *
     * Whether a provider will re-create a new instance at each call or not depends on the binding scope.
     */
    inline fun <reified T : Any> provider(tag: Any? = null): (() -> T) = _container.nonNullProvider(Kodein.Bind(typeToken<T>(), tag))

    /**
     * Gets a provider for the given type and tag, or null if none is found.
     *
     * Whether a provider will re-create a new instance at each call or not depends on the binding scope.
     */
    inline fun <reified T : Any> providerOrNull(tag: Any? = null): (() -> T)? = _container.providerOrNull(Kodein.Bind(typeToken<T>(), tag))

    /**
     * Gets an instance for the given type and tag.
     *
     * Whether the returned object is a new instance at each call or not depends on the binding scope.
     */
    inline fun <reified T : Any> instance(tag: Any? = null): T = _container.nonNullProvider<T>(Kodein.Bind(typeToken<T>(), tag)).invoke()

    /**
     * Gets an instance for the given type and tag, or null if none is found.
     *
     * Whether the returned object is a new instance at each call or not depends on the binding scope.
     */
    inline fun <reified T : Any> instanceOrNull(tag: Any? = null): T? = _container.providerOrNull<T>(Kodein.Bind(typeToken<T>(), tag))?.invoke()

    val java = JKodein(_container)
}

fun <A, T : Any> ((A) -> T).toProvider(arg: A): () -> T = { invoke(arg) }
