// These libraries are Scala Native libraries: they compile to `.nir` (linked
// into the consuming game's GDExtension `.so`) and depend on the gdext binding.
// They do NOT apply GodotScalaNativePlugin — they are published, not built into
// a `.so` themselves. So we need only sbt-scala-native here.
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.10")
