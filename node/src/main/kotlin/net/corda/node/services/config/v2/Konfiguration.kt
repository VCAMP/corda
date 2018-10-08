package net.corda.node.services.config.v2

import com.uchuhimo.konf.*
import com.uchuhimo.konf.source.DefaultLoaders
import com.uchuhimo.konf.source.Loader
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.base.FlatSource
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom add a constructor which doesn't add the schema to the Config
class Konfiguration(internal val value: Config, private val schema: ConfigSchema) : Configuration {

    override fun <TYPE> get(property: Configuration.Property<TYPE>): TYPE {

        // TODO sollecitom maybe add `prefix` here?
        return value[property.key]
    }

    override fun <TYPE> get(key: String): TYPE {

        return value[key]
    }

    override fun <TYPE> getOptional(property: Configuration.Property<TYPE>): TYPE? {

        return value.getOrNull(property.key)
    }

    override fun <TYPE> getOptional(key: String): TYPE? {

        return value.getOrNull(key)
    }

    class Builder(private val value: Config, private val schema: ConfigSchema) : Configuration.Builder {

        // TODO sollecitom make it a `val get() =` perhaps?
        override fun from() = Konfiguration.Builder.SourceSelector(value.from, schema)

        override fun build(): Configuration {

            return Konfiguration(value, schema)
        }

        class Selector(private val schema: ConfigSchema) : Configuration.Builder.Selector {

            private val spec = schema.toSpec()
            private val config = Config.invoke().also { it.addSpec(spec) }

            // TODO sollecitom perhaps try to use JvmStatic here
            override fun from(): Configuration.Builder.SourceSelector = Konfiguration.Builder.SourceSelector(config.from, schema)

            override fun empty(): Configuration = Konfiguration(config, schema)

            private fun ConfigSchema.toSpec(): Spec {

                // TODO sollecitom make it not an object
                return object : ConfigSpec(prefix) {}.also { properties.forEach { property -> property.addAsItem(it) } }
            }

            private fun <TYPE> Configuration.Property<TYPE>.addAsItem(spec: Spec): Item<TYPE> {

                // TODO sollecitom check
//        val type: JavaType? = null
                return if (this is Configuration.Property.Optional<TYPE>) {
                    object : OptionalItem<TYPE>(spec, key, defaultValue, description, null, true) {}
                } else {
                    object : RequiredItem<TYPE>(spec, key, description, null, false) {}
                }
            }
        }

        class SourceSelector(private val from: DefaultLoaders, private val schema: ConfigSchema) : Configuration.Builder.SourceSelector {

            override fun systemProperties(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(SystemPropertiesProvider.source(prefixFilter)), schema)

            override fun environment(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(EnvProvider.source(prefixFilter)), schema)

            // TODO sollecitom perhaps expose a different Selector interface for Map & Properties
            override fun properties(properties: Properties): Configuration.Builder {

                @Suppress("UNCHECKED_CAST")
                return hierarchicalMap(properties as Map<String, Any>)
            }

            // TODO sollecitom look here at the difference between .kv() and .flat()
            override fun map(map: Map<String, Any>) = Konfiguration.Builder(from.map.flat(map.mapValues { (_, value) -> value.toString() }), schema)

            override fun hierarchicalMap(map: Map<String, Any>) = Konfiguration.Builder(from.map.hierarchical(map), schema)

            // TODO sollecitom add `properties` as a supported type
            override val hocon: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.hocon, schema)

            override val yaml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.yaml, schema)

            override val xml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.xml, schema)

            override val json: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.json, schema)

            class FormatAware(private val loader: Loader, private val schema: ConfigSchema) : Configuration.Builder.SourceSelector.FormatAware {

                override fun file(path: Path) = Konfiguration.Builder(loader.file(path.toAbsolutePath().toFile()), schema)

                override fun resource(resourceName: String) = Konfiguration.Builder(loader.resource(resourceName), schema)

                override fun reader(reader: Reader) = Konfiguration.Builder(loader.reader(reader), schema)

                override fun inputStream(stream: InputStream) = Konfiguration.Builder(loader.inputStream(stream), schema)

                override fun string(rawFormat: String) = Konfiguration.Builder(loader.string(rawFormat), schema)

                override fun bytes(bytes: ByteArray) = Konfiguration.Builder(loader.bytes(bytes), schema)
            }
        }
    }

    private object SystemPropertiesProvider {

        // TODO sollecitom make it not an object
        fun source(prefixFilter: String = ""): Source = object : FlatSource(System.getProperties().toMap().onlyWithPrefix(prefixFilter), type = "system-properties") {

            override fun get(path: com.uchuhimo.konf.Path): Source {

                return QuotesAwarePropertiesEntrySource(super.get(path))
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun Properties.toMap(): Map<String, String> = this as Map<String, String>
    }

    private object EnvProvider {

        fun source(prefixFilter: String = ""): Source {

            return object : FlatSource(System.getenv().mapKeys { (key, _) -> key.toLowerCase().replace('_', '.') }.onlyWithPrefix(prefixFilter), type = "system-environment") {

                override fun get(path: com.uchuhimo.konf.Path): Source {

                    return QuotesAwarePropertiesEntrySource(super.get(path))
                }
            }
        }
    }

    private class QuotesAwarePropertiesEntrySource(private val delegate: Source) : Source by delegate {

        override fun isList() = !delegate.toText().isQuoted() && delegate.isList()

        private fun String.isQuoted() = startsWith("\"") && endsWith("\"")

        override fun toText() = delegate.toText().removePrefix("\"").removeSuffix("\"")
    }

    class Property {

        class Builder : Configuration.Property.Builder {

            override fun int(key: String, description: String): Configuration.Property<Int> = TODO("sollecitom implement")

            override fun intList(key: String, description: String): Configuration.Property<List<Int>> = TODO("sollecitom implement")

            override fun boolean(key: String, description: String): Configuration.Property<Boolean> = TODO("sollecitom implement")
            override fun booleanList(key: String, description: String): Configuration.Property<List<Boolean>> = TODO("sollecitom implement")

            override fun double(key: String, description: String): Configuration.Property<Double> = TODO("sollecitom implement")
            override fun doubleList(key: String, description: String): Configuration.Property<List<Double>> = TODO("sollecitom implement")

            override fun string(key: String, description: String): Configuration.Property<String> = KonfigProperty(key, description, String::class.java)
            override fun stringList(key: String, description: String): Configuration.Property<List<String>> = TODO("sollecitom implement")

            override fun duration(key: String, description: String): Configuration.Property<Duration> = TODO("sollecitom implement")
            override fun durationList(key: String, description: String): Configuration.Property<List<Duration>> = TODO("sollecitom implement")

            override fun value(key: String, description: String): Configuration.Property<Configuration> = TODO("sollecitom implement")
            override fun valueList(key: String, description: String): Configuration.Property<List<Configuration>> = TODO("sollecitom implement")

            override fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<ENUM> = TODO("sollecitom implement")
            override fun <ENUM : Enum<ENUM>> enumList(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<List<ENUM>> = TODO("sollecitom implement")

            override fun <TYPE> nested(key: String, type: Class<TYPE>, schema: ConfigSchema, description: String): Configuration.Property<TYPE> = TODO("sollecitom implement")
            override fun <TYPE> nestedList(key: String, type: Class<TYPE>, schema: ConfigSchema, description: String): Configuration.Property<List<TYPE>> = TODO("sollecitom implement")
        }
    }
}

private open class KonfigProperty<TYPE>(override val key: String, override val description: String, override val type: Class<TYPE>) : Configuration.Property<TYPE> {

    override fun valueIn(configuration: Configuration): TYPE {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO sollecitom check here
    override fun optional(defaultValue: TYPE?): Configuration.Property<TYPE?> = KonfigProperty.Optional(key, description, type as Class<TYPE?>, defaultValue)

    private class Optional<TYPE>(key: String, description: String, type: Class<TYPE>, override val defaultValue: TYPE) : KonfigProperty<TYPE>(key, description, type), Configuration.Property.Optional<TYPE>
}

private fun Map<String, String>.onlyWithPrefix(prefix: String): Map<String, String> {

    val prefixValue = if (prefix.isNotEmpty()) "$prefix." else prefix
    return filterKeys { key -> key.startsWith(prefixValue) }.mapKeys { (key, _) -> key.removePrefix(prefixValue) }
}