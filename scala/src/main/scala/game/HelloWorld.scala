package game

import gdext.api.GodotPrint
import gdext.classes.Node2D

case class HelloWorld(
  var speed: Double
) extends Node2D:
  override def _ready(): Unit = 
    GodotPrint.print(s"Hello from scala! (speed=$speed)")
