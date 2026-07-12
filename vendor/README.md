# Vendored jars

Scala-Native HOCON backend, vendored so neither this build nor any consumer
needs the raw-git Maven resolvers anymore. The jars' contents (classes + NIR)
are repackaged into every hocon-using module's published jar (`godot-hoccon`,
`prefabs`, `logic-constructor`) by the `vendoredHocon` settings in
`utilities/build.sbt`, which also re-declares their Maven-Central transitives
(`com.typesafe:config`, `scala-collection-compat`, `fastparse`) since a
vendored jar carries no POM.

| Jar | Origin |
|-----|--------|
| `pureconfig-core_native0.5_3-1.0.0-native.jar` | [optical002/pureconfig](https://github.com/optical002/pureconfig) fork, `maven` branch (`https://raw.githubusercontent.com/optical002/pureconfig/maven/maven`) |
| `shocon-parser_native0.5_3-1.0.0-native.jar` | [optical002/shocon](https://github.com/optical002/shocon) fork, `maven` branch (`https://raw.githubusercontent.com/optical002/shocon/maven/maven`) |

To upgrade: publish new jars from the forks, drop them in here (keep the
cross-suffix in the filename), and update the transitive pins in
`utilities/build.sbt` if the forks' POMs changed.
